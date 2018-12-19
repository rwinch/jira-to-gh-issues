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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pivotal.util.ProgressTracker;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
@Data
@Component
public class JiraClient {

	private static final Logger logger = LogManager.getLogger(JiraClient.class);

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	JiraConfig jiraConfig;

	RestOperations rest = new RestTemplate();

	WebClient webClient;


	@Autowired
	public JiraClient(JiraConfig jiraConfig) {
		this.jiraConfig = jiraConfig;
		this.webClient = WebClient.create(jiraConfig.getBaseUrl());
	}


	public JiraProject findProject(String id) {
		return rest.getForObject(getBaseUrl() + "/project/{id}", JiraProject.class, id);
	}

	private String getBaseUrl() {
		return jiraConfig.getBaseUrl() + "/rest/api/2";
	}

	public List<JiraIssue> findIssues(String jql) {

		logger.info("Loading issues, query: \"{}\"", jql);

		List<JiraIssue> issues = new ArrayList<>();
		Long startAt = 0L;
		System.out.print("Page");
		while(true) {
			System.out.print(" [" + startAt + "-" + (startAt + 1000) + "]");
			JiraSearchResult searchResult = rest.getForObject(
					getBaseUrl() + "/search?maxResults=1000&startAt={0}&jql={jql}&fields=" + JiraIssue.FIELD_NAMES,
					JiraSearchResult.class, startAt, jql);
			issues.addAll(searchResult.getIssues());
			startAt = searchResult.getNextStartAt();
			if (startAt == null) {
				System.out.println("");
				break;
			}
		}

		logger.info("{} issues loaded ", issues.size());

		issues.forEach(JiraIssue::initFixAndBackportVersions);
		return issues;
	}

	public List<JiraIssue> findIssuesVotesAndCommits(String jql) {
		List<JiraIssue> issues = findIssues(jql);
		logger.info("Loading votes and commits for {} issues", issues.size());
		populateVotesAndCommitsConcurrent(issues, 10);
//		populateVotesAndCommitsSequential(issues);
		verify(issues);
		return issues;
	}

	/**
	 * @issues the issues to populate
	 * @param concurrency how many issues to fetch data for concurrently. Actual concurrency
	 * for the server is x 2 since we fire two calls (votes and commits) per iteration
	 */
	private void populateVotesAndCommitsConcurrent(List<JiraIssue> issues, int concurrency) {
		ProgressTracker tracker = new ProgressTracker(issues.size());
		Flux.fromIterable(issues)
				.flatMap(issue -> {
					Mono<Map<String, Object>> votesResult = webClient.get()
							.uri("/rest/api/2/issue/{id}/votes", issue.getId())
							.retrieve()
							.bodyToMono(MAP_TYPE)
							.timeout(Duration.ofSeconds(10))
							.retry(3);
					Mono<Map<String, Object>> commitsResult = webClient.get()
							.uri("/rest/dev-status/1.0/issue/detail?issueId={id}&applicationType=github&dataType=repository", issue.getId())
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
				.blockLast();
	}

	private void populateVotesAndCommitsSequential(List<JiraIssue> issues) {
		ProgressTracker tracker = new ProgressTracker(issues.size());
		for (JiraIssue issue : issues) {
			tracker.updateForIteration();

			String url = getBaseUrl() + "/issue/{id}/votes";
			Map<String, Object> result = rest.exchange(url, HttpMethod.GET, null, MAP_TYPE, issue.getId()).getBody();
			issue.setVotes((int) result.get("votes"));

			// No official API, below is the URL used in the browser
			url = jiraConfig.getBaseUrl() + "/rest/dev-status/1.0/issue/detail?issueId={id}&applicationType=github&dataType=repository";
			result = rest.exchange(url, HttpMethod.GET, null, MAP_TYPE, issue.getId()).getBody();
			issue.setCommitUrls(extractCommits(result));
		}
		tracker.stopProgress();
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

	private void verify(List<JiraIssue> issues) {

		List<JiraIssue> incomplete = issues.stream()
				.filter(issue -> issue.getVotes() == -1 || issue.getCommitUrls() == null)
				.collect(Collectors.toList());

		Assert.isTrue(incomplete.isEmpty(), "No votes and/or commits: " + incomplete.stream()
				.map(issue -> issue.getKey() + ", votes=" + issue.getVotes() + ", commitUrls=" + issue.getCommitUrls())
				.collect(Collectors.toList()));
	}

}
