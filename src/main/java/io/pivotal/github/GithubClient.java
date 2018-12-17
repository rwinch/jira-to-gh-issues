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
package io.pivotal.github;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pivotal.LabelHandler;
import io.pivotal.MilestoneFilter;
import io.pivotal.jira.IssueLink;
import io.pivotal.jira.JiraAttachment;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraIssue.Fields;
import io.pivotal.jira.JiraUser;
import io.pivotal.jira.JiraVersion;
import io.pivotal.util.MarkupEngine;
import io.pivotal.util.MarkupManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Milestone;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.LabelService;
import org.eclipse.egit.github.core.service.MilestoneService;
import org.joda.time.DateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * @author Rob Winch
 */
@Data
@Component
public class GithubClient {

	static final Logger logger = LogManager.getLogger(GithubClient.class);

	private static final List<String> SUPPRESSED_LINK_TYPES = Arrays.asList("relates to", "is related to");


	GithubConfig config;

	MarkupManager markup;

	MilestoneFilter milestoneFilter;

	LabelHandler labelHandler;

	Map<String, String> jiraToGithubUsername;

	RestTemplate rest = new ExtendedRestTemplate();

	GitHubClient client = new ExtendedEgitGitHubClient();

	IRepositoryIdProvider repositoryIdProvider;

	DateTime migrationDateTime = DateTime.now();


	@Autowired
	public GithubClient(GithubConfig config, MarkupManager markup,
			MilestoneFilter milestoneFilter, LabelHandler labelHandler) {

		this.config = config;
		this.markup = markup;
		this.milestoneFilter = milestoneFilter;
		this.labelHandler = labelHandler;
		this.repositoryIdProvider = RepositoryId.createFromId(this.config.getRepositorySlug());
	}


	@SuppressWarnings("unused")
	@Autowired
	public void setUserMappingResource(@Value("classpath:jira-to-github-users.properties") Resource resource) throws IOException {
		Properties properties = new Properties();
		properties.load(resource.getInputStream());
		jiraToGithubUsername = new HashMap<>();

		for (final String name : properties.stringPropertyNames()) {
			jiraToGithubUsername.put(name, properties.getProperty(name));
		}
	}


	public void deleteRepository() throws IOException {
		if(!this.config.isDeleteCreateRepositorySlug()) {
			return;
		}
		String slug = this.config.getRepositorySlug();

		CommitService commitService = new CommitService(this.client);
		try {
			commitService.getCommits(repositoryIdProvider);
			throw new IllegalStateException("Attempting to delete a repository that has commits. Terminating!");
		} catch(RequestException e) {
			if(e.getStatus() != 404 & e.getStatus() != 409) {
				throw new IllegalStateException(
						"Attempting to delete a repository, but it appears the repository has commits. Terminating!", e);
			}
		}

		logger.info("Deleting repository {}", slug);

		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(
				"https://api.github.com/repos/" + slug).queryParam("access_token", this.config.getAccessToken());
		rest.delete(uri.toUriString());
	}

	public void createRepository() {
		if(!this.config.isDeleteCreateRepositorySlug()) {
			return;
		}
		String slug = this.config.getRepositorySlug();

		logger.info("Creating repository {}", slug);

		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("https://api.github.com/user/repos")
				.queryParam("access_token", this.config.getAccessToken());
		Map<String, String> repository = new HashMap<>();
		repository.put("name", slug.split("/")[1]);
		rest.postForEntity(uri.toUriString(), repository, String.class);
	}

	public void createMilestones(List<JiraVersion> versions) throws IOException {
		MilestoneService milestones = new MilestoneService(this.client);
		versions = versions.stream().filter(milestoneFilter).collect(Collectors.toList());
		for (JiraVersion version : versions) {
			Milestone milestone = new Milestone();
			milestone.setTitle(version.getName());
			milestone.setState(version.isReleased() ? "closed" : "open");
			if (version.getReleaseDate() != null) {
				milestone.setDueOn(version.getReleaseDate().toDate());
			}
			milestones.createMilestone(repositoryIdProvider, milestone);
		}
	}

	public void createLabels() throws IOException {
		LabelService labelService = new LabelService(this.client);
		for (Label label : labelHandler.getAllLabels()) {
			logger.debug("Creating label: \"{}\"", label.getName());
			labelService.createLabel(repositoryIdProvider, label);
		}
	}


	// https://gist.github.com/jonmagic/5282384165e0f86ef105#start-an-issue-import

	public void createIssues(List<JiraIssue> issues) throws IOException {

		Map<String, JiraUser> userLookup = scrapeUsers(issues);
		this.markup.configureUserLookup(userLookup);

		Map<String, Milestone> milestones = new MilestoneService(this.client)
				.getMilestones(repositoryIdProvider, "all")
				.stream()
				.collect(Collectors.toMap(Milestone::getTitle, Function.identity()));

		logger.info("Starting import");
		Map<String,ImportedIssue> importedIssues = new HashMap<>();
		for (int i = 0, issuesSize = issues.size(); i < issuesSize; i++) {
			JiraIssue jiraIssue = issues.get(i);
			ImportedIssue importedIssue = importIssue(jiraIssue, milestones);
			importedIssues.put(jiraIssue.getKey(), importedIssue);
			// Every 100 or so, verify imported issues succeeded
			if (i % 100 == 0 && i != 0) {
				importedIssues.values().forEach(this::findGithubIssueNumber);
			}
		}
		// At the end, verify all imported issues succeeded
		importedIssues.values().forEach(this::findGithubIssueNumber);
		logger.info("Imported {} issues", issues.size());


		MultiValueMap<Milestone, ImportedIssue> backportMap = groupBackportsByMilestone(importedIssues, milestones);
		logger.info("Creating backport holder issues for {} milestones", backportMap.size());
		backportMap.keySet().forEach(milestone -> {
			logger.debug(milestone.getTitle());
			GithubIssue ghIssue = initMilestoneBackportIssue(milestone, backportMap.get(milestone));
			ImportGithubIssue toImport = new ImportGithubIssue();
			toImport.setIssue(ghIssue);
			executeIssueImport(toImport);
		});


		logger.info("Replacing Jira links with GitHub issue numbers");
		List<ImportedIssue> issuesToUpdate = importedIssues.values().stream()
				.filter(importedIssue -> {
					Fields fields = importedIssue.getJiraIssue().getFields();
					return fields.getParent() != null || !fields.getSubtasks().isEmpty() || !fields.getIssuelinks().isEmpty();
				})
				.collect(Collectors.toList());

		for(ImportedIssue importedIssue : issuesToUpdate) {
			int ghIssueNumber = findGithubIssueNumber(importedIssue);
			String body = importedIssue.getImportResponse().getImportIssue().getIssue().getBody();
			body = replaceLinksToIssues(body, importedIssue.getJiraIssue(), importedIssues);
			String slug = this.config.getRepositorySlug();
			URI uri = UriComponentsBuilder
					.fromUriString("https://api.github.com/repos/" + slug + "/issues/" + ghIssueNumber)
					.build().toUri();
			BodyBuilder requestBuilder = RequestEntity.patch(uri)
					.accept(new MediaType("application", "vnd.github.symmetra-preview+json"))
					.header("Authorization", "token " + this.config.getAccessToken());
			rest.exchange(requestBuilder.body(Collections.singletonMap("body", body)), Void.class);
		}
	}

	private Map<String, JiraUser> scrapeUsers(List<JiraIssue> issues) {
		Map<String, JiraUser> userLookup = new HashMap<>();
		for (JiraIssue issue : issues) {
			Fields fields = issue.getFields();
			userLookup.put(fields.getReporter().getKey(), fields.getReporter());
			for (JiraComment comment : fields.getComment().getComments()) {
				userLookup.put(comment.getAuthor().getKey(), comment.getAuthor());
			}
		}
		return userLookup;
	}

	private ImportedIssue importIssue(JiraIssue jiraIssue, Map<String, Milestone> milestones) {
		ImportGithubIssue importGithubIssue = new ImportGithubIssue();
		importGithubIssue.setIssue(initGithubIssue(jiraIssue, milestones));
		importGithubIssue.setComments(initComments(jiraIssue));
		ImportGithubIssueResponse importResponse = executeIssueImport(importGithubIssue);
		return new ImportedIssue(jiraIssue, importResponse);
	}

	private GithubIssue initGithubIssue(JiraIssue issue, Map<String, Milestone> milestones) {

		Fields fields = issue.getFields();
		DateTime updated = fields.getUpdated();
		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setTitle("[" + issue.getKey() + "] " + fields.getSummary());

		MarkupEngine engine = markup.engine(issue.getFields().getCreated());
		JiraUser reporter = fields.getReporter();
		String reporterLink = engine.link(reporter.getDisplayName(), reporter.getBrowserUrl());
		String body = "**" + reporterLink + "** opened **" +
				engine.link(issue.getKey(), issue.getBrowserUrl()) + "** and commented\n";
		if (fields.getReferenceUrl() != null) {
			body += "\n_Reference URL:_\n" + fields.getReferenceUrl() + "\n";
		}
		if(fields.getDescription() != null) {
			body += "\n" + engine.convert(fields.getDescription());
		}
		String jiraDetails = initJiraDetails(issue, engine);
		if (!StringUtils.isEmpty(jiraDetails)) {
			body += "\n\n---\n" + jiraDetails;
		}
		ghIssue.setBody(body);

		// From the Jira docs ("Working with workflows"):
		//
		// In JIRA, an issue is either open or closed, based on the value of its 'Resolution' field — not its 'Status' field.
		// An issue is open if its resolution field has not been set.
		// An issue is closed if its resolution field has a value (e.g. Fixed, Cannot Reproduce).
		// This is true regardless of the current value of the issue's status field (Open, In Progress, etc).

		boolean closed = fields.getResolution() != null;
		ghIssue.setClosed(closed);
		if (closed) {
			ghIssue.setClosedAt(updated);
		}
		JiraUser assignee = fields.getAssignee();
		if (assignee != null) {
			String ghUsername = jiraToGithubUsername.get(assignee.getKey());
			if (ghUsername != null) {
				ghIssue.setAssignee(ghUsername);
			}
		}
		ghIssue.setCreatedAt(fields.getCreated());
		ghIssue.setUpdatedAt(updated);
		if (issue.getFixVersion() != null) {
			Milestone milestone = milestones.get(issue.getFixVersion().getName());
			if (milestone != null) {
				ghIssue.setMilestone(milestone.getNumber());
			}
		}
		Set<String> labels = labelHandler.getLabelsFor(issue);
		ghIssue.getLabels().addAll(labels);
		return ghIssue;
	}

	private String initJiraDetails(JiraIssue issue, MarkupEngine engine) {
		Fields fields = issue.getFields();
		String jiraDetails = "";
		JiraIssue parent = fields.getParent();
		if (!fields.getVersions().isEmpty()) {
			jiraDetails += fields.getVersions().stream().map(JiraVersion::getName)
					.collect(Collectors.joining(", ", "\n**Affects:** ", "\n"));
		}
		List<JiraAttachment> attachments = fields.getAttachment();
		if (!attachments.isEmpty()) {
			boolean multiLine = attachments.size() > 1;
			jiraDetails += attachments.stream()
					.map(attachment -> {
						String contentUrl = attachment.getContent();
						String filename = attachment.getFilename();
						String size = attachment.getSizeToDisplay();
						return engine.link(filename, contentUrl) + " (_" + size + "_)";
					})
					.collect(Collectors.joining("\n", "\n**Attachment" + (multiLine ? "s:**\n" : ":** "), "\n"));
		}
		if (parent != null) {
			String key = parent.getKey();
			jiraDetails += "\nThis issue is a sub-task of " + engine.link(key, parent.getBrowserUrl()) + "\n";
		}
		if (!fields.getSubtasks().isEmpty()) {
			jiraDetails += fields.getSubtasks().stream()
					.map(subtask -> {
						String key = subtask.getKey();
						String browserUrl = issue.getBrowserUrlFor(key);
						return engine.link(key, browserUrl) + " " + subtask.getFields().getSummary();
					})
					.collect(Collectors.joining("\n", "\n**Sub-tasks:**\n", "\n"));
		}
		if (!fields.getIssuelinks().isEmpty()) {
			jiraDetails += fields.getIssuelinks().stream()
					.map(link -> {
						// For now link to Jira. Later we'll make another pass to replace with GH issue numbers.
						String key;
						String linkType;
						String title;
						if (link.getOutwardIssue() != null) {
							key = link.getOutwardIssue().getKey();
							linkType = link.getType().getOutward();
							title = link.getOutwardIssue().getFields().getSummary();
						}
						else {
							key = link.getInwardIssue().getKey();
							linkType = link.getType().getInward();
							title = link.getInwardIssue().getFields().getSummary();
						}
						return engine.link(key, issue.getBrowserUrlFor(key)) + " " + title +
								(!SUPPRESSED_LINK_TYPES.contains(linkType) ? " (_**\"" + linkType + "\"**_)" : "");
					})
					.collect(Collectors.joining("\n", "\n**Issue Links:**\n", "\n"));
		}
		List<String> references = new ArrayList<>();
		if (fields.getPullRequestUrl() != null) {
			references.add(fields.getPullRequestUrl());
		}
		if (!issue.getCommitUrls().isEmpty()) {
			references.add(String.join(", ", issue.getCommitUrls()));
		}
		if (!references.isEmpty()) {
			boolean multiLine = references.size() > 1;
			jiraDetails += references.stream().collect(
					Collectors.joining("\n", "\n**Referenced From:**" + (multiLine ? "\n" : " "), "\n"));
		}
		if (!issue.getBackportVersions().isEmpty()) {
			jiraDetails += issue.getBackportVersions().stream().map(JiraFixVersion::getName)
					.collect(Collectors.joining(", ", "\n**Backported to:** ", "\n"));
		}
		int watchCount = issue.getFields().getWatches().getWatchCount();
		if (issue.getVotes() > 0 || watchCount >= 5) {
			jiraDetails += "\n" + issue.getVotes() + " votes, " + watchCount + " watchers\n";
		}
		return jiraDetails;
	}

	private List<GithubComment> initComments(JiraIssue issue) {
		Fields fields = issue.getFields();
		MarkupEngine engine = markup.engine(fields.getCreated());
		List<GithubComment> comments = new ArrayList<>();
		for (JiraComment jiraComment : fields.getComment().getComments()) {
			GithubComment comment = new GithubComment();
			String userUrl = jiraComment.getAuthor().getBrowserUrl();
			String body = "**" + engine.link(jiraComment.getAuthor().getDisplayName(), userUrl) + "** commented\n\n";
			body += engine.convert(jiraComment.getBody());
			comment.setBody(body);
			comment.setCreatedAt(jiraComment.getCreated());
			comments.add(comment);
		}
		return comments;
	}

	private GithubIssue initMilestoneBackportIssue(Milestone milestone, List<ImportedIssue> backportIssues) {
		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setMilestone(milestone.getNumber());
		ghIssue.setTitle(milestone.getTitle() + " Backport Issues");
		ghIssue.setCreatedAt(new DateTime(milestone.getCreatedAt().getTime()));
		if (milestone.getState().equals("closed")) {
			ghIssue.setClosed(true);
			ghIssue.setClosedAt(new DateTime(milestone.getDueOn()));
		}
		String body = backportIssues.stream()
				.map(importedIssue -> {
					int ghIssueNumber = findGithubIssueNumber(importedIssue);
					String summary = importedIssue.getJiraIssue().getFields().getSummary();
					return "**#" + ghIssueNumber + "** - " + summary;
				})
				.collect(Collectors.joining("\n"));
		ghIssue.setBody(body);
		return ghIssue;
	}

	private ImportGithubIssueResponse executeIssueImport(ImportGithubIssue importIssue) {
		URI uri = UriComponentsBuilder
				.fromUriString("https://api.github.com/repos/" + this.config.getRepositorySlug())
				.pathSegment("import", "issues")
				.build()
				.toUri();
		BodyBuilder request = RequestEntity.post(uri)
				.accept(new MediaType("application", "vnd.github.golden-comet-preview+json"))
				.header("Authorization", "token " + this.config.getAccessToken());

		ResponseEntity<ImportGithubIssueResponse> result = rest.exchange(request.body(importIssue), ImportGithubIssueResponse.class);
		ImportGithubIssueResponse importResponse = result.getBody();
		Assert.notNull(importResponse, "No response for " + request);
		importResponse.setImportIssue(importIssue);
		return importResponse;
	}

	private int findGithubIssueNumber(ImportedIssue importedIssue) {
		if(importedIssue.getIssueNumber() != null) {
			return importedIssue.getIssueNumber();
		}
		String importIssuesUrl = importedIssue.getImportResponse().getUrl();

		URI uri = UriComponentsBuilder
				.fromUriString(importIssuesUrl)
				.build()
				.toUri();
		RequestEntity<Void> request = RequestEntity.get(uri)
				.accept(new MediaType("application", "vnd.github.golden-comet-preview+json"))
				.header("Authorization", "token " + this.config.getAccessToken())
				.build();

		ParameterizedTypeReference<Map<String, Object>> returnType =
				new ParameterizedTypeReference<Map<String, Object>>() {};

		long secondsToWait = 1;
		while(true) {
			ResponseEntity<Map<String, Object>> result = rest.exchange(request, returnType);
			Map<String, Object> body = result.getBody();
			Assert.notNull(body, "No body for " + request);
			String url = (String) body.get("issue_url");
			String status = (String) body.get("status");
			if ("failed".equals(status)) {
				throw new IllegalStateException("Ticket import failed: " + body);
			}
			else if ("pending".equals(status)) {
				logger.debug("Issue {} is still pending import, waiting {} seconds to look up GitHub issue number again",
						importedIssue.getJiraIssue().getKey(), secondsToWait);
				try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(secondsToWait));
				}
				catch (InterruptedException e) {
					// Ignore
				}
				secondsToWait = (1 + secondsToWait) * 2;
				continue;
			}
			Assert.notNull(url, "No URL for imported issue: " + body);
			UriComponents parts = UriComponentsBuilder.fromUriString(url).build();
			List<String> segments = parts.getPathSegments();
			int issueNumber = Integer.parseInt(segments.get(segments.size() - 1));
			importedIssue.setIssueNumber(issueNumber);
			return issueNumber;
		}
	}

	private MultiValueMap<Milestone, ImportedIssue> groupBackportsByMilestone(
			Map<String, ImportedIssue> importedIssues, Map<String, Milestone> milestones) {

		MultiValueMap<Milestone, ImportedIssue> backportMap = new LinkedMultiValueMap<>();
		for (ImportedIssue importedIssue : importedIssues.values()) {
			for (JiraFixVersion version : importedIssue.getJiraIssue().getBackportVersions()) {
				Milestone milestone = milestones.get(version.getName());
				// TODO: shouldn't there be a milestone for *any* backport version?
				// Run some tests on the data to check for anomalies, e.g. based on pseudo versions like Contributions Welcome
				if (milestone != null) {
					backportMap.add(milestone, importedIssue);
				}
			}
		}
		return backportMap;
	}

	private String replaceLinksToIssues(String body, JiraIssue jiraIssue, Map<String, ImportedIssue> importedIssues) {
		JiraIssue parent = jiraIssue.getFields().getParent();
		if (parent != null) {
			body = replaceLinksToIssue(body, parent.getKey(), importedIssues);
		}
		for (JiraIssue subtask : jiraIssue.getFields().getSubtasks()) {
			body = replaceLinksToIssue(body, subtask.getKey(), importedIssues);
		}
		for (IssueLink link : jiraIssue.getFields().getIssuelinks()) {
			String key = link.getOutwardIssue() != null ? link.getOutwardIssue().getKey() : link.getInwardIssue().getKey();
			body = replaceLinksToIssue(body, key, importedIssues);
		}
		return body;
	}

	private String replaceLinksToIssue(String body, String targetIssueKey, Map<String, ImportedIssue> importedIssues) {
		ImportedIssue linkedIssue = importedIssues.get(targetIssueKey);
		// It could be null if linked to Jira ticket from another project
		if (linkedIssue != null) {
			body = body.replaceFirst("\\[" + targetIssueKey + "]\\(.*" + targetIssueKey + "\\?redirect=false\\)",
					"#" + findGithubIssueNumber(linkedIssue));
		}
		return body;
	}


	private static void waitForAtLeastOneSecondBetweenRequests() {
		try {
			// From https://developer.github.com/v3/guides/best-practices-for-integrators/#dealing-with-rate-limits
			// If you're making a large number of POST, PATCH, PUT, or DELETE requests
			// for a single user or client ID, wait at least one second between each request.

			Thread.sleep(1000);
		}
		catch (InterruptedException ex) {
			// ignore
		}
	}


	@SuppressWarnings("unused")
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class ImportStatusResponse {
		@JsonProperty("issue_url")
		String issueUrl;
	}

	@Data
	@RequiredArgsConstructor
	static class ImportedIssue {
		/**
		 * The GitHub issue number (may be null). This is filled out after the
		 * importResponse is post processed.
		 */
		Integer issueNumber;
		final JiraIssue jiraIssue;
		final ImportGithubIssueResponse importResponse;
	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	@Data
	static class ImportGithubIssueResponse {
		ImportGithubIssue importIssue;
		String url;
		String status;
		List<Error> errors;

		@SuppressWarnings("unused")
		public boolean isFailed() {
			return "failed".equals(status);
		}

		@Data
		static class Error {
			String code;
			String field;
			String location;
			String resource;
			String value;
		}
	}


	private static class ExtendedRestTemplate extends RestTemplate {

		ExtendedRestTemplate() {
			super(new HttpComponentsClientHttpRequestFactory());
		}

		@Override
		protected <T> T doExecute(URI url, HttpMethod method, RequestCallback requestCallback,
				ResponseExtractor<T> responseExtractor) throws RestClientException {
			return doExecute(url, method, requestCallback, responseExtractor, 45);
		}

		private <T> T doExecute(URI url, HttpMethod method, RequestCallback requestCallback,
				ResponseExtractor<T> responseExtractor, long abuseSleepTimeInSeconds) throws RestClientException {
			try {
				waitForAtLeastOneSecondBetweenRequests();
				RequestCallback decoratedRequestCallback = request -> {
					if (requestCallback != null) {
						requestCallback.doWithRequest(request);
					}
					GithubClient.logger.debug("{} {} {}", method, url.getPath(), request.getHeaders());
				};
				return super.doExecute(url, method, decoratedRequestCallback, responseExtractor);
			} catch(HttpClientErrorException e) {
				HttpHeaders headers = e.getResponseHeaders();
				Assert.notNull(headers, "No headers: " + method + " " + url);
				long sleep;
				if(!"0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
					if(e.getResponseBodyAsString().contains("https://developer.github.com/v3/#abuse-rate-limits")) {
						GithubClient.logger.info("Received https://developer.github.com/v3/#abuse-rate-limits with no indication of how long to wait. Let's guess & wait "+abuseSleepTimeInSeconds+ " seconds.");
						sleep = TimeUnit.SECONDS.toMillis(abuseSleepTimeInSeconds);
					} else {
						GithubClient.logger.error(e.getResponseBodyAsString());
						throw e;
					}
				} else {
					GithubClient.logger.info("Received X-RateLimit-Reset. Waiting to do additional work");
					String reset = headers.getFirst("X-RateLimit-Reset");
					Assert.notNull(reset, "No X-RateLimit-Reset: " + headers);
					sleep = (1000 * Long.parseLong(reset)) - System.currentTimeMillis();
				}
				sleepFor(sleep);
			}
			return doExecute(url, method, requestCallback, responseExtractor, abuseSleepTimeInSeconds * 2);
		}

		@Override
		protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
			GithubClient.logger.debug("{} {X-RateLimit-Remaining:{}}",
					response.getStatusCode(), response.getHeaders().getFirst("X-RateLimit-Remaining"));
			super.handleResponse(url, method, response);
		}

		private void sleepFor(long sleep) {
			if(sleep < 1) {
				return;
			}
			long endTime = System.currentTimeMillis() + sleep;
			GithubClient.logger.debug("Sleeping until {}", new DateTime(endTime));
			for(long now = System.currentTimeMillis(); now < endTime; now = System.currentTimeMillis()) {
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					// Ignore
				}
			}
			GithubClient.logger.debug("Continuing");
		}
	}


	private class ExtendedEgitGitHubClient extends GitHubClient {

		@Override
		protected HttpURLConnection configureRequest(HttpURLConnection request) {
			setOAuth2Token(config.getAccessToken());
			logger.debug("{} {}", request.getRequestMethod().toUpperCase(), request.getURL().getPath());
			waitForAtLeastOneSecondBetweenRequests();
			HttpURLConnection result = super.configureRequest(request);
			result.setRequestProperty(HEADER_ACCEPT, MediaType.APPLICATION_JSON_VALUE);
			return result;
		}

		@Override
		protected GitHubClient updateRateLimits(HttpURLConnection request) {
			GitHubClient client = super.updateRateLimits(request);
			logger.debug("{} {X-RateLimit-Remaining:{}}", getHttpStatus(request), getRemainingRequests());
			return client;
		}

		private HttpStatus getHttpStatus(HttpURLConnection request) {
			try {
				int code = request.getResponseCode();
				return HttpStatus.valueOf(code);
			}
			catch (IOException ex) {
				ex.printStackTrace();
				return null;
			}
		}
	}

}
