/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.jira;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.pivotal.util.ProgressTracker;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
@Data
@Component
public class JiraClient {

	/**
	 * The max number of issues expected in the Jira project. It should be some reasonably high number,
	 * so all issues will be fetched, in multiples of 1000. This is necessary because we don't
	 * politely fetch one page at a time, but rather get 5 at a time concurrently. This number gives
	 * a hint where to stop so we avoid running into a bunch of 400 errors before realizing to stop.
	 */
	public static final int MAX_ISSUE_COUNT_HINT = 20000;

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};

	private static final Logger logger = LogManager.getLogger(JiraClient.class);


	JiraConfig jiraConfig;

	WebClient webClient;


	@Autowired
	public JiraClient(JiraConfig jiraConfig) {
		this.jiraConfig = jiraConfig;
		WebClient.Builder builder = WebClient.builder().baseUrl(jiraConfig.getBaseUrl() + "/rest/api/2");
		if (jiraConfig.getUser() != null) {
			builder = builder.defaultHeaders(headers ->
					headers.setBasicAuth(jiraConfig.getUser(), jiraConfig.getPassword()));
		}
		this.webClient = builder.build();
	}


	public JiraProject findProject(String id) {
		return webClient.get().uri("/project/{id}", id).retrieve().bodyToMono(JiraProject.class).block();
	}

	public List<JiraIssue> findIssues(String jql) {
		return getAndCollectIssues(jql).block();
	}

	public List<JiraIssue> findIssuesVotesAndCommits(
			String jql, Function<List<JiraIssue>, List<JiraIssue>> filterIssuesToImport) {

		return getAndCollectIssues(jql)
				.flatMap(issues -> {
					// Load votes and commits only for issues not already imported
					return populateVotesAndCommits(filterIssuesToImport.apply(issues))
							.then(Mono.just(issues));
				})
				.block();
	}

	private Mono<List<JiraIssue>> getAndCollectIssues(String jql) {
		return getIssues(jql).collectList()
				.doOnNext(issues -> {
					logger.info("Found {} issues", issues.size());

					Map<String, JiraIssue> backportSubtasks = issues.stream()
							.filter(issue -> issue.getFields().getIssuetype().getName().equalsIgnoreCase("Backport"))
							.filter(issue -> issue.getFields().getFixVersions() != null)
							.collect(Collectors.toMap(JiraIssue::getKey, o -> o));

					issues.forEach(issue -> issue.initFixAndBackportVersions(backportSubtasks));
				});
	}

	private Flux<JiraIssue> getIssues(String jql) {
		int pageSize = 1000;
		logger.info("Loading issues (1000 per page) for jql=\"{}\"", jql);
		int concurrency = 5; // we could go higher but each brings large amount of data to convert in parallel
		return Flux.range(0, MAX_ISSUE_COUNT_HINT / 1000)
				.flatMap(page -> {
					int startAt = page * pageSize;
					System.out.print((page + 1) + " ");
					return webClient.get()
							.uri("/search?maxResults=1000&startAt={0}&jql={jql}&fields=" + JiraIssue.FIELD_NAMES, startAt, jql)
							.retrieve()
							.bodyToMono(JiraSearchResult.class)
							.onErrorResume(ex -> {
								logger.error("page " + page + ": " + ex.getMessage(), ex);
								return Mono.empty();
							});

				}, concurrency)
				.sort(Comparator.comparingLong(JiraSearchResult::getStartAt))
				.concatMapIterable(JiraSearchResult::getIssues)
				.doOnComplete(() -> System.out.println("complete"));
	}

	/**
	 * @param issues the issues to populate
	 */
	private Mono<Void> populateVotesAndCommits(List<JiraIssue> issues) {
		logger.info("Loading votes and commits (2 requests per issue/iteration)", issues.size());
		ProgressTracker tracker = new ProgressTracker(issues.size(), 50, 1000, logger.isDebugEnabled());
		int concurrency = 8; // 16 concurrent requests (2 per flatMap)s
		return Flux.fromIterable(issues)
				.flatMap(issue -> {
					Mono<Map<String, Object>> votesResult = webClient.get()
							.uri("/issue/{id}/votes", issue.getId())
							.retrieve()
							.bodyToMono(MAP_TYPE)
							.timeout(Duration.ofSeconds(10))
							.retry(3);
					Mono<Map<String, Object>> commitsResult = webClient.get()
							.uri(builder -> builder
									.replacePath("/rest/dev-status/1.0/issue/detail")
									.query("issueId={id}&applicationType=github&dataType=repository")
									.build(issue.getId()))
							.retrieve()
							.bodyToMono(MAP_TYPE)
							.timeout(Duration.ofSeconds(10))
							.retry(3);
					return Mono.zip(Mono.just(issue), votesResult, commitsResult);
				}, concurrency)
				.doOnNext(tuple -> {
					tuple.getT1().setVotes((int) tuple.getT2().get("votes"));
					tuple.getT1().setCommitUrls(extractCommits(tuple.getT3()));
					tracker.updateForIteration();
				})
				.doOnComplete(tracker::stopProgress)
				.then();
	}

	@SuppressWarnings("unchecked")
	private List<String> extractCommits(Map<String, Object> result) {
		List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("detail");
		if (!details.isEmpty()) {
			List<Map<String, Object>> repos = (List<Map<String, Object>>) details.get(0).get("repositories");
			if (!repos.isEmpty()) {
				List<Map<String, Object>> commits = (List<Map<String, Object>>) repos.get(0).get("commits");
				return commits.stream().map(map -> (String) map.get("url")).collect(Collectors.toList());
			}
		}
		return Collections.emptyList();
	}

}
