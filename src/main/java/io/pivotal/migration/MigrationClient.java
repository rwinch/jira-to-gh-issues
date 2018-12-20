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
package io.pivotal.migration;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pivotal.github.GithubComment;
import io.pivotal.github.GithubConfig;
import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraAttachment;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraConfig;
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
 * @author Rossen Stoyanchev
 */
@Data
@Component
public class MigrationClient {

	private static final Logger logger = LogManager.getLogger(MigrationClient.class);

	private static final List<String> SUPPRESSED_LINK_TYPES = Arrays.asList("relates to", "is related to");

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
			new ParameterizedTypeReference<Map<String, Object>>() {};


	private final GithubConfig config;

	private final MarkupManager markup;

	private final MilestoneFilter milestoneFilter;

	private final LabelHandler labelHandler;

	/** Used to replace Jira issue keys that appear in comments with links */
	private final Pattern jiraIssueKeyPattern;

	/** For assignees */
	Map<String, String> jiraToGithubUsername;

	// From https://developer.github.com/v3/guides/best-practices-for-integrators/#dealing-with-rate-limits
	// If you're making a large number of POST, PATCH, PUT, or DELETE requests
	// for a single user or client ID, wait at least one second between each request.
	private final RateLimitHelper rateLimitHelper = new RateLimitHelper();

	private final ExtendedRestTemplate rest = new ExtendedRestTemplate(rateLimitHelper);

	private final GitHubClient client = new ExtendedEgitGitHubClient(rateLimitHelper);

	private final IRepositoryIdProvider repositoryIdProvider;

	private final DateTime migrationDateTime = DateTime.now();

	private final BodyBuilder importRequestBuilder;


	@Autowired
	public MigrationClient(GithubConfig config, JiraConfig jiraConfig, MarkupManager markup,
			MilestoneFilter milestoneFilter, LabelHandler labelHandler) {

		this.config = config;
		this.markup = markup;
		this.milestoneFilter = milestoneFilter;
		this.labelHandler = labelHandler;
		this.jiraIssueKeyPattern = Pattern.compile("(" + jiraConfig.getProjectId() + "-[0-9]{1,5}+)");
		this.repositoryIdProvider = RepositoryId.createFromId(this.config.getRepositorySlug());
		this.importRequestBuilder = initImportRequestBuilder();
	}

	private BodyBuilder initImportRequestBuilder() {
		String slug = this.config.getRepositorySlug();
		URI uri = URI.create("https://api.github.com/repos/" + slug + "/import/issues");
		return RequestEntity.post(uri)
				.accept(new MediaType("application", "vnd.github.golden-comet-preview+json"))
				.header("Authorization", "token " + this.config.getAccessToken());
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


	public boolean deleteRepository() throws IOException {
		if(!this.config.isDeleteCreateRepositorySlug()) {
			return false;
		}
		String slug = this.config.getRepositorySlug();

		CommitService commitService = new CommitService(this.client);
		try {
			commitService.getCommits(repositoryIdProvider);
			throw new IllegalStateException("Attempting to delete a repository that has commits. Terminating!");
		} catch(RequestException e) {
			if(e.getStatus() != 404 & e.getStatus() != 409) {
				throw new IllegalStateException("Attempting to delete a repository, but it appears the repository has commits. Terminating!", e);
			}
		}

		logger.info("Deleting repository {}", slug);

		String url = UriComponentsBuilder
				.fromUriString("https://api.github.com/repos/" + slug)
				.queryParam("access_token", this.config.getAccessToken())
				.toUriString();

		// Don't use the ExtendedRestTemplate here which retries and suppresses failure
		rest.delete(url);
		return true;
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
		ResponseEntity<String> entity = rest.postForEntity(uri.toUriString(), repository, String.class);
		Assert.notNull(entity, "No response");
	}

	public void createMilestones(List<JiraVersion> versions) throws IOException {
		MilestoneService milestones = new MilestoneService(this.client);
		versions = versions.stream().filter(milestoneFilter).collect(Collectors.toList());
		logger.info("Creating {} milestones", versions.size());
		ProgressTracker tracker = new ProgressTracker(versions.size(), 1, 50, logger.isDebugEnabled());
		for (JiraVersion version : versions) {
			tracker.updateForIteration();
			Milestone milestone = new Milestone();
			milestone.setTitle(version.getName());
			milestone.setState(version.isReleased() ? "closed" : "open");
			if (version.getReleaseDate() != null) {
				milestone.setDueOn(version.getReleaseDate().toDate());
			}
			milestones.createMilestone(repositoryIdProvider, milestone);
		}
		tracker.stopProgress();
	}

	public void createLabels() throws IOException {
		LabelService labelService = new LabelService(this.client);
		Set<Label> labels = labelHandler.getAllLabels();
		logger.info("Creating labels: {}", labels);
		ProgressTracker tracker = new ProgressTracker(labels.size(), logger.isDebugEnabled());
		for (Label label : labels) {
			tracker.updateForIteration();
			logger.debug("Creating label: \"{}\"", label.getName());
			labelService.createLabel(repositoryIdProvider, label);
		}
		tracker.stopProgress();
	}


	// https://gist.github.com/jonmagic/5282384165e0f86ef105#start-an-issue-import

	public void createIssues(List<JiraIssue> allIssues, MigrationContext context) {

		logger.info("Collecting list of users from all issues");
		Map<String, JiraUser> users = collectUsers(allIssues);
		this.markup.configureUserLookup(users);

		logger.info("Retrieving list of milestones");
		Map<String, Milestone> milestones = retrieveMilestones();

		logger.info("Collecting lists of backport issues by milestone");
		MultiValueMap<Milestone, JiraIssue> backportMap = collectBackports(allIssues, milestones);

		logger.info("Preparing for import (wiki to markdown, select labels, format Jira details, etc)");
		List<JiraIssue> importIssues = context.filterRemaingIssuesToImport(allIssues);
		List<ImportGithubIssue> importData = importIssues.stream()
				.map(jiraIssue -> {
					ImportGithubIssue issueToImport = new ImportGithubIssue();
					issueToImport.setIssue(initGithubIssue(jiraIssue, milestones));
					issueToImport.setComments(initComments(jiraIssue));
					return issueToImport;
				})
				.collect(Collectors.toList());

		logger.info("Starting to import (2 requests per issue/iteration) {} issues", importIssues.size());
		ProgressTracker tracker1 = new ProgressTracker(importIssues.size(), 4, 200, logger.isDebugEnabled());
		List<ImportedIssue> importedIssues = new ArrayList<>(importIssues.size());
		for (int i = 0, issuesSize = importIssues.size(); i < issuesSize; i++) {
			tracker1.updateForIteration();
			ImportGithubIssueResponse importResponse = executeIssueImport(importData.get(i), context);
			importedIssues.add(new ImportedIssue(importIssues.get(i), null, importResponse));
			if (i % 50 == 0 && i != 0) {
				for (int j = i - 50; j <= i; j++) {
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
					.map(issue -> issue.getMilestone().getTitle())
					.collect(Collectors.toList());
			List<String> succeeded = backportIssueHolders.stream()
					.filter(i -> checkImportResult(i, context))
					.map(issue -> issue.getMilestone().getTitle())
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

	private Map<String, Milestone> retrieveMilestones() {
		try {
			return new MilestoneService(this.client)
					.getMilestones(repositoryIdProvider, "all")
					.stream()
					.collect(Collectors.toMap(Milestone::getTitle, Function.identity()));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private MultiValueMap<Milestone, JiraIssue> collectBackports(List<JiraIssue> issues, Map<String, Milestone> milestones) {
		MultiValueMap<Milestone, JiraIssue> backportMap = new LinkedMultiValueMap<>();
		for (JiraIssue jiraIssue : issues) {
			for (JiraFixVersion version : jiraIssue.getBackportVersions()) {
				Milestone milestone = milestones.get(version.getName());
				if (milestone != null) {
					backportMap.add(milestone, jiraIssue);
				}
			}
		}
		return backportMap;
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
				engine.link(issue.getKey(), issue.getBrowserUrl() + "?redirect=false") + "** and commented\n";
		if (fields.getReferenceUrl() != null) {
			body += "\n_Reference URL:_\n" + fields.getReferenceUrl() + "\n";
		}
		if(fields.getDescription() != null) {
			String description = replaceSprKeys(fields.getDescription(), engine, issue);
			body += "\n" + engine.convert(description);
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
		// Avoid using actual assignees while in test mode
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
			Milestone milestone = milestones.get(issue.getFixVersion().getName());
			if (milestone != null) {
				ghIssue.setMilestone(milestone.getNumber());
			}
		}
		Set<String> labels = labelHandler.getLabelsFor(issue);
		ghIssue.getLabels().addAll(labels);
		return ghIssue;
	}

	private String replaceSprKeys(String text, MarkupEngine engine, JiraIssue issue) {
		Matcher matcher = jiraIssueKeyPattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			matcher.appendReplacement(sb, engine.link(key, issue.getBrowserUrlFor(key)));
		}
		matcher.appendTail(sb);
		return sb.toString();
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
			jiraDetails += "\nThis issue is a sub-task of " + engine.link(key, parent.getBrowserUrl()) + "\n";
		}
		if (!fields.getSubtasks().isEmpty()) {
			jiraDetails += fields.getSubtasks().stream()
					.map(subtask -> {
						String key = subtask.getKey();
						String browserUrl = issue.getBrowserUrlFor(key);
						return "- " + engine.link(key, browserUrl) + " " + subtask.getFields().getSummary();
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
			String content = replaceSprKeys(jiraComment.getBody(), engine, issue);
			body += engine.convert(content);
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
			response = rest.exchange(request, ImportGithubIssueResponse.class).getBody();
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
					body = rest.exchange(request, MAP_TYPE).getBody();
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
			Milestone milestone, List<JiraIssue> backportIssues, MigrationContext context) {

		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setMilestone(milestone.getNumber());
		ghIssue.setTitle(milestone.getTitle() + " Backported Issues");
		ghIssue.setCreatedAt(new DateTime(milestone.getCreatedAt().getTime()));
		if (milestone.getState().equals("closed")) {
			ghIssue.setClosed(true);
			ghIssue.setClosedAt(new DateTime(milestone.getDueOn()));
		}
		String body = backportIssues.stream()
				.map(jiraIssue -> {
					String jiraKey = jiraIssue.getKey();
					Integer ghIssueId = context.getGitHubIssueId(jiraKey);
					if (ghIssueId == null) {
						context.addFailureMessage(milestone.getTitle() +
								" backport issues holder is a missing the GitHub issue id for " + jiraKey + "\n");
					}
					MarkupEngine engine = markup.engine(jiraIssue.getFields().getCreated());
					String summary = jiraIssue.getFields().getSummary();
					summary = engine.convertBackportIssueSummary(summary);
					return "**#" + ghIssueId + "** - " + summary;
				})
				.collect(Collectors.joining("\n"));
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
		final Milestone milestone;

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



	private static final List<String> rateLimitedMethods = Arrays.asList("POST", "PATCH", "PUT", "DELETE");


	private static class ExtendedRestTemplate extends RestTemplate {

		private final RateLimitHelper rateLimitHelper;


		ExtendedRestTemplate(RateLimitHelper rateLimitHelper) {
			super(new HttpComponentsClientHttpRequestFactory());
			this.rateLimitHelper = rateLimitHelper;
		}


		@Override
		protected <T> T doExecute(URI url, HttpMethod method, RequestCallback requestCallback,
				ResponseExtractor<T> responseExtractor) throws RestClientException {
			return doExecuteExtended(url, method, requestCallback, responseExtractor);
		}

		private <T> T doExecuteExtended(URI url, HttpMethod method, RequestCallback requestCallback,
				ResponseExtractor<T> responseExtractor) throws RestClientException {

			try {
				RequestCallback decoratedRequestCallback = request -> {
					if (requestCallback != null) {
						requestCallback.doWithRequest(request);
					}
					MigrationClient.logger.debug("{} {} {}", method, url.getPath(), request.getHeaders());
				};
				if (rateLimitedMethods.contains(method.name())) {
					rateLimitHelper.obtainPermitToCall();
				}
				return super.doExecute(url, method, decoratedRequestCallback, responseExtractor);
			}
			catch(HttpClientErrorException ex) {
				HttpHeaders headers = ex.getResponseHeaders();
				String requestInfo = method + " " + url.getPath() + " " + headers;
				if (headers == null) {
					MigrationClient.logger.error("No headers for " + requestInfo);
					throw ex;
				}
				long timeToSleep = 0;
				String retryAfter = headers.getFirst("Retry-After");
				if (retryAfter != null) {
					MigrationClient.logger.debug("Received Retry-After: " + retryAfter + " for " + requestInfo);
					timeToSleep = 1000 * Integer.parseInt(retryAfter);
				}
				else if ("0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
					String reset = headers.getFirst("X-RateLimit-Reset");
					if (reset != null) {
						MigrationClient.logger.debug("Received X-RateLimit-Reset: " + reset + " for " + requestInfo);
						timeToSleep = (1000 * Long.parseLong(reset)) - System.currentTimeMillis();
					}
					else {
						MigrationClient.logger.error("X-RateLimit-Remaining:0 but no X-RateLimit-Reset: " + requestInfo);
						throw ex;
					}
				}
				else {
					throw ex;
				}
				try {
					if (timeToSleep > 0) {
						Thread.sleep(timeToSleep);
					}
				}
				catch (InterruptedException interruptedEx) {
					// Ignore
				}
			}
			// Recurse and retry...
			return doExecuteExtended(url, method, requestCallback, responseExtractor);
		}

		@Override
		protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
			MigrationClient.logger.debug("{} {X-RateLimit-Remaining:{}}",
					response.getStatusCode(), response.getHeaders().getFirst("X-RateLimit-Remaining"));
			super.handleResponse(url, method, response);
		}
	}


	private class ExtendedEgitGitHubClient extends GitHubClient {

		private final RateLimitHelper rateLimitHelper;


		ExtendedEgitGitHubClient(RateLimitHelper rateLimitHelper) {
			this.rateLimitHelper = rateLimitHelper;
		}

		@Override
		protected HttpURLConnection configureRequest(HttpURLConnection request) {
			setOAuth2Token(config.getAccessToken());
			logger.debug("{} {}", request.getRequestMethod().toUpperCase(), request.getURL().getPath());
			rateLimitHelper.obtainPermitToCall();
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
