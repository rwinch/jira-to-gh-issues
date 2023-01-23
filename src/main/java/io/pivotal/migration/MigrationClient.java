/*
 * Copyright 2002-2023 the original author or authors.
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
package io.pivotal.migration;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pivotal.github.GitHubRestTemplate;
import io.pivotal.github.GithubComment;
import io.pivotal.github.GithubConfig;
import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
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
import io.pivotal.util.ProgressTracker;
import io.pivotal.util.RateLimitHelper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;


/**
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
@Data
@Component
public class  MigrationClient {

	private static final Logger logger = LogManager.getLogger(MigrationClient.class);

	private static final List<String> SUPPRESSED_LINK_TYPES = Arrays.asList("relates to", "is related to");

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};

	private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE =
			new ParameterizedTypeReference<List<Map<String, Object>>>() {};

	private static final String GITHUB_URL = "https://api.github.com";


	private final GithubConfig config;

	private final MarkupManager markup;

	private final MilestoneFilter milestoneFilter;

	private final LabelHandler labelHandler;

	private final IssueProcessor issueProcessor;

	/** For assignees */
	Map<String, String> jiraToGithubUsername;

	// From https://developer.github.com/v3/guides/best-practices-for-integrators/#dealing-with-rate-limits
	// If you're making a large number of POST, PATCH, PUT, or DELETE requests
	// for a single user or client ID, wait at least one second between each request.
	private final RateLimitHelper rateLimitHelper = new RateLimitHelper();

	private final GitHubRestTemplate rest = new GitHubRestTemplate(rateLimitHelper, logger);

	private final DateTime migrationDateTime = DateTime.now();

	private final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis();

	private final BodyBuilder importRequestBuilder;

	private int importBatchSize = 100;


	@Autowired
	public MigrationClient(GithubConfig config, MarkupManager markup,
			MilestoneFilter milestoneFilter, LabelHandler labelHandler, IssueProcessor issueProcessor) {

		this.config = config;
		this.markup = markup;
		this.milestoneFilter = milestoneFilter;
		this.labelHandler = labelHandler;
		this.issueProcessor = issueProcessor;
		this.importRequestBuilder =
				getRepositoryRequestBuilder(HttpMethod.POST, "/import/issues")
						.accept(new MediaType("application", "vnd.github.golden-comet-preview+json"));
	}

	private BodyBuilder getRepositoryRequestBuilder(HttpMethod httpMethod, String path) {
		String url = GITHUB_URL + "/repos/" + this.config.getRepositorySlug() + path;
		return RequestEntity.method(httpMethod, URI.create(url))
				.header(HttpHeaders.AUTHORIZATION, "token " + this.config.getAccessToken());
	}

	@SuppressWarnings("unused")
	@Autowired
	public void setUserMappingResource(
			@Value("classpath:jira-to-github-users.properties") Resource resource) throws IOException {

		Properties properties = new Properties();
		properties.load(resource.getInputStream());
		jiraToGithubUsername = new HashMap<>();

		for (final String name : properties.stringPropertyNames()) {
			jiraToGithubUsername.put(name, properties.getProperty(name));
		}
	}


	public boolean deleteRepository() {
		if(!this.config.isDeleteCreateRepositorySlug()) {
			return false;
		}

		String slug = this.config.getRepositorySlug();
		logger.info("Deleting repository {}", slug);

		BodyBuilder requestBuilder = getRepositoryRequestBuilder(HttpMethod.DELETE, "");
		getRest().exchange(requestBuilder.build(), MAP_TYPE);

		return true;
	}

	public void createRepository() {
		if(!this.config.isDeleteCreateRepositorySlug()) {
			return;
		}

		String slug = this.config.getRepositorySlug();
		logger.info("Creating repository {}", slug);

		Map<String, String> repository = new HashMap<>();
		repository.put("name", slug.split("/")[1]);
		repository.put("private", "true");

		RequestEntity<Map<String, String>> requestEntity =
				RequestEntity.post(URI.create(GITHUB_URL + "/user/repos"))
						.header(HttpHeaders.AUTHORIZATION, "token " + this.config.getAccessToken())
						.body(repository);

		getRest().exchange(requestEntity, MAP_TYPE);
	}

	public void createMilestones(List<JiraVersion> versions) {
		BodyBuilder requestBuilder = getRepositoryRequestBuilder(HttpMethod.POST, "/milestones");
		versions = versions.stream().filter(milestoneFilter).collect(Collectors.toList());
		logger.info("Creating {} milestones", versions.size());
		ProgressTracker tracker = new ProgressTracker(versions.size(), 1, 50, logger.isDebugEnabled());
		for (JiraVersion version : versions) {
			tracker.updateForIteration();
			Map<String, String> map = new LinkedHashMap<>();
			map.put("title", version.getName());
			map.put("state", version.isReleased() ? "closed" : "open");
			if (version.getReleaseDate() != null) {
				map.put("due_on", version.getReleaseDate().toString(dateTimeFormatter));
			}
			this.getRest().exchange(requestBuilder.body(map), MAP_TYPE);
		}
		tracker.stopProgress();
	}

	public void createLabels() {

		BodyBuilder bodyBuilder = getRepositoryRequestBuilder(HttpMethod.POST, "/labels");

		Set<Map<String, String>> labels = labelHandler.getAllLabels();
		logger.info("Creating labels: {}", labels);
		ProgressTracker tracker = new ProgressTracker(labels.size(), logger.isDebugEnabled());
		for (Map<String, String> map : labels) {
			tracker.updateForIteration();
			logger.debug("Creating label: \"{}\"", map);
			getRest().exchange(bodyBuilder.body(map), MAP_TYPE);
		}
		tracker.stopProgress();
	}


	// https://gist.github.com/jonmagic/5282384165e0f86ef105#start-an-issue-import

	public void createIssues(List<JiraIssue> publicIssues, List<String> restrictedIssueKeys,
			MigrationContext context) {

		logger.info("Collecting list of users from all issues");
		Map<String, JiraUser> users = collectUsers(publicIssues);
		this.markup.configureUserLookup(users);

		logger.info("Retrieving list of milestones");
		Map<String, Map<String, Object>> milestones = retrieveMilestones();

		logger.info("Collecting lists of backport issues by milestone");
		MultiValueMap<Map<String, Object>, JiraIssue> backportMap = collectBackports(publicIssues, milestones);

		logger.info("Preparing for import (wiki to markdown, select labels, format Jira details, etc)");
		List<JiraIssue> importIssues = context.filterRemaingIssuesToImport(publicIssues);
		List<ImportGithubIssue> importData = importIssues.stream()
				.map(jiraIssue -> {
					issueProcessor.beforeConversion(jiraIssue);
					ImportGithubIssue issueToImport = new ImportGithubIssue();
					issueToImport.setIssue(initGithubIssue(jiraIssue, milestones, restrictedIssueKeys));
					issueToImport.setComments(initComments(jiraIssue));
					issueProcessor.beforeImport(jiraIssue, issueToImport);
					return issueToImport;
				})
				.collect(Collectors.toList());

		logger.info("Starting to import {} issues (2 requests per issue/iteration)", importIssues.size());
		ProgressTracker tracker1 = new ProgressTracker(importIssues.size(), 4, 200, logger.isDebugEnabled());
		List<ImportedIssue> importedIssues = new ArrayList<>(importIssues.size());
		for (int i = 0, issuesSize = importIssues.size(); i < issuesSize; i++) {
			tracker1.updateForIteration();
			ImportGithubIssueResponse importResponse = executeIssueImport(importData.get(i), context);
			importedIssues.add(new ImportedIssue(importIssues.get(i), null, importResponse));
			if (i % importBatchSize == 0 && i != 0) {
				for (int j = i - importBatchSize; j <= i; j++) {
					if (!checkImportResult(importedIssues.get(j), context)) {
						logger.error("Detected import failure for " + importIssues.get(i).getKey());
						break;
					}
				}
			}
		}
		tracker1.stopProgress();

		logger.info("Checking remaining import results");
		importedIssues.forEach(issue -> checkImportResult(issue, context));
		if (context.getFailedImportCount() == 0) {
			logger.info("0 failures");
		}
		else {
			int failed = context.getFailedImportCount();
			int total = importedIssues.size();
			logger.error(failed + " failed, " + (total - failed) + " succeeded, " + total + " total");
			return;
		}

		logger.info("{} backport issue holders to create", backportMap.size());
		if (backportMap.isEmpty()) {
			return;
		}
		List<ImportedIssue> backportIssueHolders = new ArrayList<>(backportMap.size());
		ProgressTracker tracker2 = new ProgressTracker(backportIssueHolders.size(), logger.isDebugEnabled());
		backportMap.keySet().forEach(milestone -> {
			tracker2.updateForIteration();
			GithubIssue ghIssue = initMilestoneBackportIssue(milestone, backportMap.get(milestone), context);
			ImportGithubIssue toImport = new ImportGithubIssue();
			toImport.setIssue(ghIssue);
			ImportGithubIssueResponse importResponse = executeIssueImport(toImport, context);
			backportIssueHolders.add(new ImportedIssue(null, milestone, importResponse));
		});
		tracker2.stopProgress();
		logger.info("Checking import results for backport issue holders");
		backportIssueHolders.forEach(issue -> checkImportResult(issue, context));
		if (context.getFailedImportCount() == 0) {
			logger.info("0 failures");
		}
		else {
			List<String> failed = backportIssueHolders.stream()
					.filter(issue -> !checkImportResult(issue, context))
					.map(issue -> (String) issue.getMilestone().get("title"))
					.collect(Collectors.toList());
			List<String> succeeded = backportIssueHolders.stream()
					.filter(i -> checkImportResult(i, context))
					.map(issue -> (String) issue.getMilestone().get("title"))
					.collect(Collectors.toList());
			logger.error("Failed:\n" + failed + "\nSucceeded:\n" + succeeded);
		}
	}

	private Map<String, JiraUser> collectUsers(List<JiraIssue> issues) {
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

	private Map<String, Map<String, Object>> retrieveMilestones() {
		Map<String, Map<String, Object>> result = new LinkedHashMap<>();
		for (int page = 1; ; page++){
			String path = "/milestones?state=all&per_page=100&page=" + page;
			RequestEntity<?> request = getRepositoryRequestBuilder(HttpMethod.GET, path).build();
			List<Map<String, Object>> milestones = getRest().exchange(request, LIST_OF_MAPS_TYPE).getBody();
			if (CollectionUtils.isEmpty(milestones)) {
				break;
			}
			milestones.forEach(milestone -> result.put((String) milestone.get("title"), milestone));
		}
		return result;
	}

	private MultiValueMap<Map<String, Object>, JiraIssue> collectBackports(
			List<JiraIssue> issues, Map<String, Map<String, Object>> milestones) {

		MultiValueMap<Map<String, Object>, JiraIssue> backportMap = new LinkedMultiValueMap<>();
		for (JiraIssue jiraIssue : issues) {
			for (JiraFixVersion version : jiraIssue.getBackportVersions()) {
				Map<String, Object> milestone = milestones.get(version.getName());
				if (milestone != null) {
					backportMap.add(milestone, jiraIssue);
				}
			}
		}
		return backportMap;
	}

	private GithubIssue initGithubIssue(JiraIssue issue, Map<String, Map<String, Object>> milestones,
			List<String> restrictedIssues) {

		Fields fields = issue.getFields();
		DateTime updated = fields.getUpdated();
		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setTitle(fields.getSummary() + " [" + issue.getKey() + "]");

		MarkupEngine engine = markup.engine(issue.getFields().getCreated());
		JiraUser reporter = fields.getReporter();
		String reporterLink = engine.link(reporter.getDisplayName(), reporter.getBrowserUrl());
		String jiraIssueLink = engine.link(issue.getKey(), issue.getBrowserUrl() + "?redirect=false");
		String body = "**" + reporterLink + "** opened **" + jiraIssueLink + "**"
				+ (fields.getComment().hasRestrictedComments() ? "*" : "")
				+ " and commented\n";
		String description = fields.getDescription();
		if(description != null) {
			// Remove trailing horizontal line
			int index = description.lastIndexOf("----");
			if (index != -1) {
				if (index + 4 == description.length() || description.substring(index + 4).matches("[\\s]*")) {
					description = description.substring(0, index);
				}
			}
			body += "\n" + engine.convert(description);
		}
		String jiraDetails = initJiraDetails(issue, engine, milestones, restrictedIssues);
		body += "\n\n---\n" + (StringUtils.hasText(jiraDetails) ? jiraDetails : "No further details from " + jiraIssueLink);
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
		// Can't use actual assignees in test mode (they're probably not contributors in the test project)
		if (!config.isDeleteCreateRepositorySlug()) {
			JiraUser assignee = fields.getAssignee();
			if (assignee != null) {
				String ghUsername = jiraToGithubUsername.get(assignee.getKey());
				if (ghUsername != null) {
					ghIssue.setAssignee(ghUsername);
				}
			}
		}
		ghIssue.setCreatedAt(fields.getCreated());
		ghIssue.setUpdatedAt(updated);
		if (issue.getFixVersion() != null) {
			Map<String, Object> milestone = milestones.get(issue.getFixVersion().getName());
			if (milestone != null) {
				ghIssue.setMilestone((Integer) milestone.get("number"));
			}
		}
		ghIssue.getLabels().addAll(labelHandler.getLabelsFor(issue));
		return ghIssue;
	}

	private String initJiraDetails(JiraIssue issue, MarkupEngine engine,
			Map<String, Map<String, Object>> milestones, List<String> restrictedIssues) {

		Fields fields = issue.getFields();
		String jiraDetails = "";
		JiraIssue parent = fields.getParent();
		if (!fields.getVersions().isEmpty()) {
			jiraDetails += fields.getVersions().stream().map(JiraVersion::getName)
					.collect(Collectors.joining(", ", "\n**Affects:** ", "\n"));
		}
		if (fields.getReferenceUrl() != null) {
			jiraDetails += "\n**Reference URL:** " + fields.getReferenceUrl() + "\n";
		}
		List<JiraAttachment> attachments = fields.getAttachment();
		if (!attachments.isEmpty()) {
			jiraDetails += attachments.stream()
					.map(attachment -> {
						String contentUrl = attachment.getContent();
						String filename = attachment.getFilename();
						String size = attachment.getSizeToDisplay();
						return "- " + engine.link(filename, contentUrl) + " (_" + size + "_)";
					})
					.collect(Collectors.joining("\n", "\n**Attachments:**\n", "\n"));
		}
		if (parent != null) {
			String key = parent.getKey();
			String issueType = fields.getIssuetype().getName();
			String subTaskType = "Backport".equalsIgnoreCase(issueType) ? "backport sub-task" : "sub-task";
			jiraDetails += "\nThis issue is a " + subTaskType + " of " + engine.link(key, parent.getBrowserUrl()) + "\n";
		}
		List<JiraIssue> subtasks = fields.getSubtasks().stream()
				.filter(subtask -> !restrictedIssues.contains(subtask.getKey()))
				.collect(Collectors.toList());
		if (!subtasks.isEmpty()) {
			jiraDetails += subtasks.stream()
					.map(subtask -> {
						String key = subtask.getKey();
						String browserUrl = issue.getBrowserUrlFor(key);
						String summary = subtask.getFields().getSummary();
						summary = engine.convert(summary); // escape annotations (colliding with GitHub mentions)
						return "- " + engine.link(key, browserUrl) + " " + summary;
					})
					.collect(Collectors.joining("\n", "\n**Sub-tasks:**\n", "\n"));
		}
		List<IssueLink> issueLinks = fields.getIssuelinks().stream()
				.filter(link -> link.getOutwardIssue() != null ?
						!restrictedIssues.contains(link.getOutwardIssue().getKey()) :
						!restrictedIssues.contains(link.getInwardIssue().getKey()))
				.collect(Collectors.toList());
		if (!issueLinks.isEmpty()) {
			jiraDetails += issueLinks.stream()
					.filter(link -> !restrictedIssues.contains(link.getOutwardIssue() != null ?
							link.getOutwardIssue().getKey() : link.getInwardIssue().getKey()))
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
						title = engine.convert(title); // escape annotations (colliding with GitHub mentions)
						return "- " + engine.link(key, issue.getBrowserUrlFor(key)) + " " + title +
								(!SUPPRESSED_LINK_TYPES.contains(linkType) ? " (_**\"" + linkType + "\"**_)" : "");
					})
					.collect(Collectors.joining("\n", "\n**Issue Links:**\n", "\n"));
		}
		List<String> references = new ArrayList<>();
		if (fields.getPullRequestUrl() != null) {
			// Avoid inserting links to actual pull requests while in testing mode since
			// that generates events in the timeline of the pull requests, e.g.
			// https://github.com/spring-projects/spring-framework/pull/1997
			if (!getConfig().isDeleteCreateRepositorySlug()) {
				references.add("pull request " + fields.getPullRequestUrl());
			}
		}
		if (!issue.getCommitUrls().isEmpty()) {
			references.add(issue.getCommitUrls().stream().collect(Collectors.joining(", ", "commits ", "")));
		}
		if (!references.isEmpty()) {
			jiraDetails += references.stream().collect(Collectors.joining(", and ", "\n**Referenced from:** ", "\n"));
		}
		if (!issue.getBackportVersions().isEmpty()) {
			jiraDetails += issue.getBackportVersions().stream()
					.map(jiraFixVersion -> {
						String name = jiraFixVersion.getName();
						Map<String, Object> milestone = milestones.get(name);
						if (milestone != null) {
							String baseUrl = "https://github.com/" + config.getRepositorySlug();
							return engine.link(name, baseUrl + "/milestone/" + milestone.get("number") + "?closed=1");
						}
						else {
							return name;
						}
					})
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
		for (JiraComment jiraComment : fields.getComment().getVisibleComments()) {
			GithubComment comment = new GithubComment();
			JiraUser author = jiraComment.getAuthor();
			String body = "**" + engine.link(author.getDisplayName(), author.getBrowserUrl()) + "** commented\n\n";
			body += engine.convert(jiraComment.getBody());
			comment.setBody(body);
			comment.setCreatedAt(jiraComment.getCreated());
			comments.add(comment);
		}
		return comments;
	}

	private ImportGithubIssueResponse executeIssueImport(ImportGithubIssue importIssue, MigrationContext context) {
		ImportGithubIssueResponse response = null;
		Throwable failure = null;
		try {
			RequestEntity<ImportGithubIssue> request = importRequestBuilder.body(importIssue);
			response = getRest().exchange(request, ImportGithubIssueResponse.class).getBody();
			if (response != null) {
				response.setImportIssue(importIssue);
			}
			else {
				failure = new IllegalStateException("No body in ResponseEntity");
			}
		}
		catch (Throwable ex) {
			failure = ex;
		}
		if (failure != null) {
			String message = "Failed to POST import for \"" + importIssue.getIssue().getTitle() + "\"";
			logger.error(message, failure.getMessage());
			context.addFailureMessage(message + ": " + failure.getMessage());
		}
		return response;
	}

	private boolean checkImportResult(ImportedIssue importedIssue, MigrationContext context) {
		if(importedIssue.getIssueNumber() != null) {
			return true;
		}
		if (importedIssue.getFailure() != null) {
			return false;
		}
		JiraIssue jiraIssue = importedIssue.getJiraIssue();
		try {
			if (importedIssue.getImportResponse() == null) {
				importedIssue.setFailure("No body from import request");
				return false;
			}
			String importUrl = importedIssue.getImportResponse().getUrl();
			URI uri = UriComponentsBuilder.fromUriString(importUrl).build().toUri();
			RequestEntity<Void> request = RequestEntity.get(uri)
					.accept(new MediaType("application", "vnd.github.golden-comet-preview+json"))
					.header("Authorization", "token " + this.config.getAccessToken())
					.build();
			while (true) {
				Map<String, Object> body;
				try {
					body = getRest().exchange(request, MAP_TYPE).getBody();
				}
				catch (RestClientException ex) {
					logger.error("Import failed: " + importUrl, ex);
					importedIssue.setFailure(ex.getMessage());
					return false;
				}
				if (body == null) {
					importedIssue.setFailure("No body from import result request");
					return false;
				}
				String url = (String) body.get("issue_url");
				String status = (String) body.get("status");
				if ("failed".equals(status)) {
					importedIssue.setFailure("status: " + body);
					return false;
				}
				else if ("pending".equals(status)) {
					logger.debug("{} import still pending. Waiting 1 second",
							jiraIssue != null ? jiraIssue.getKey() : importUrl);
					rateLimitHelper.obtainPermitToCall();
					continue;
				}
				if (url == null) {
					importedIssue.setFailure("No URL for imported issue: " + body);
					return false;
				}
				UriComponents parts = UriComponentsBuilder.fromUriString(url).build();
				List<String> segments = parts.getPathSegments();
				int issueNumber = Integer.parseInt(segments.get(segments.size() - 1));
				importedIssue.setIssueNumber(issueNumber);
				return true;
			}
		}
		finally {
			context.addImportResult(importedIssue);
		}
	}

	private GithubIssue initMilestoneBackportIssue(
			Map<String, Object> milestone, List<JiraIssue> backportIssues, MigrationContext context) {

		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setMilestone((Integer) milestone.get("number"));
		ghIssue.setTitle(milestone.get("title") + " Backported Issues");
		DateTime dueOnDateTime = this.dateTimeFormatter.parseDateTime((String) milestone.get("due_on"));
		ghIssue.setCreatedAt(dueOnDateTime);
		if (milestone.get("state").equals("closed")) {
			ghIssue.setClosed(true);
			ghIssue.setClosedAt(dueOnDateTime);
		}
		String body = backportIssues.stream()
				.map(jiraIssue -> {
					String jiraKey = jiraIssue.getKey();
					Integer ghIssueId = context.getGitHubIssueId(jiraKey);
					if (ghIssueId == null) {
						context.addFailureMessage(milestone.get("title") +
								" backport issues holder is a missing the GitHub issue id for " + jiraKey + "\n");
					}
					return "- " + jiraIssue.getFields().getSummary() + " #" + ghIssueId;
				})
				.collect(Collectors.joining("\n"));
		JiraIssue backportIssue = backportIssues.get(0);
		MarkupEngine engine = markup.engine(backportIssue.getFields().getCreated());
		body = engine.convert(body);  // escape annotations (colliding with GitHub mentions)
		ghIssue.setBody(body);
		return ghIssue;
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

		// The below two (issueNumber and failure) will be null, until we get the
		// result from the import.

		Integer issueNumber;
		String failure;

		// The below two are mutually exclusive, depending on whether:
		//  1) It's an issue imported from Jira
		//  2) It's a backport issue holder for a specific milestone

		final JiraIssue jiraIssue;
		final Map<String, Object> milestone;

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

}
