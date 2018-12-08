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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pivotal.jira.IssueLink;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraComponent;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraIssue.Fields;
import io.pivotal.jira.JiraIssueType;
import io.pivotal.jira.JiraUser;
import io.pivotal.jira.JiraVersion;
import io.pivotal.util.MarkupEngine;
import io.pivotal.util.MarkupManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Milestone;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.IssueService;
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

	GithubConfig config;

	JiraConfig jiraConfig;

	MarkupManager markup;

	Map<String, String> jiraToGithubUsername;

	MilestoneHandler milestoneHandler;

	LabelMapper componentLabelMapper;

	LabelMapper issueTypeLabelMapper;

	RestTemplate rest = new ExtendedRestTemplate();

	GitHubClient client = new ExtendedEgitGitHubClient();

	IRepositoryIdProvider repositoryIdProvider;


	@Autowired
	public GithubClient(GithubConfig config, JiraConfig jiraConfig, MarkupManager markup) {
		this.config = config;
		this.jiraConfig = jiraConfig;
		this.markup = markup;
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

		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("https://api.github.com/repos/" + slug)
				.queryParam("access_token", this.config.getAccessToken());
		rest.delete(uri.toUriString());
	}

	public void createRepository() {
		if(!this.config.isDeleteCreateRepositorySlug()) {
			return;
		}
		String slug = this.config.getRepositorySlug();

		UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("https://api.github.com/user/repos")
				.queryParam("access_token", this.config.getAccessToken());
		Map<String, String> repository = new HashMap<>();
		repository.put("name", slug.split("/")[1]);
		rest.postForEntity(uri.toUriString(), repository, String.class);
	}

	public void createMilestones(List<JiraVersion> versions) throws IOException {
		Assert.notNull(componentLabelMapper, "MilestoneHandler not configured");
		MilestoneService milestones = new MilestoneService(this.client);
		for (JiraVersion version : versions) {
			Milestone milestone = milestoneHandler.mapVersion(version);
			if (milestone != null) {
				milestones.createMilestone(repositoryIdProvider, milestone);
			}
		}
	}

	public void createComponentLabels(List<JiraComponent> components) throws IOException {
		Assert.notNull(componentLabelMapper, "ComponentLabelMapper not configured");
		LabelService labels = new LabelService(this.client);
		for (JiraComponent component : components) {
			Label label = this.componentLabelMapper.apply(component.getName());
			if (label != null) {
				labels.createLabel(repositoryIdProvider, label);
			}
		}
	}

	public void createIssueTypeLabels(List<JiraIssueType> issueTypes) throws IOException {
		Assert.notNull(componentLabelMapper, "IssueTypeMapper not configured");
		LabelService labels = new LabelService(this.client);
		for (JiraIssueType issueType : issueTypes) {
			Label label = this.issueTypeLabelMapper.apply(issueType.getName());
			if (label != null) {
				labels.createLabel(repositoryIdProvider, label);
			}
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

		Map<String,ImportedIssue> importedIssues = new HashMap<>();
		for (int i = 0, issuesSize = issues.size(); i < issuesSize; i++) {
			JiraIssue jiraIssue = issues.get(i);
			System.out.println(jiraIssue.getKey());
			ImportedIssue importedIssue = importIssue(jiraIssue, milestones);
			importedIssues.put(jiraIssue.getKey(), importedIssue);
			// Every 100 or so, verify imported issues succeeded
			if (i % 100 == 0 && i != 0) {
				importedIssues.values().forEach(importedIssue1 -> findGithubIssueNumber(importedIssue1));
			}
		}
		System.out.println("Imported " + issues.size() + " issues in total\n");

		System.out.println("Checking backport versions");
		int b = 0;
		MultiValueMap<Milestone, ImportedIssue> backportMap = new LinkedMultiValueMap<>();
		for(ImportedIssue importedIssue : importedIssues.values()) {
			b += importedIssue.getBackportVersions().size();
			for (JiraFixVersion version : importedIssue.getBackportVersions()) {
				Milestone milestone = milestones.get(version.getName());
				if (milestone != null) {
					backportMap.add(milestone, importedIssue);
				}
			}
		}
		System.out.println("Creating " + backportMap.size() + " backport issues (one per milestone), " +
				"containing " + b + " backports in total\n");
		backportMap.keySet().forEach(milestone -> {
			System.out.println("Backport issue for " + milestone.getTitle());
			GithubIssue ghIssue = initMilestoneBackportIssue(milestone, backportMap.get(milestone));
			ImportGithubIssue toImport = new ImportGithubIssue();
			toImport.setIssue(ghIssue);
			executeIssueImport(toImport);
		});

		System.out.println("Replacing Jira links with GH issue numbers");
		IssueService issueService = new IssueService(this.client);
		for(ImportedIssue importedIssue : importedIssues.values()) {
			List<IssueLink> issueLinks = importedIssue.getJiraIssue().getFields().getIssuelinks();
			if (issueLinks.isEmpty()) {
				continue;
			}
			int ghIssueNumber = findGithubIssueNumber(importedIssue);
			List<Comment> comments = issueService.getComments(repositoryIdProvider, ghIssueNumber);
			Comment infoComment = comments.get(0);
			String body = infoComment.getBody();
			for (IssueLink link : issueLinks) {
				String key = link.getOutwardIssue() != null ? link.getOutwardIssue().getKey() : link.getInwardIssue().getKey();
				ImportedIssue linkedIssue = importedIssues.get(key);
				// It could be null if linked to Jira ticket from another project
				if (linkedIssue != null) {
					int ghLinkedIssueNumber = findGithubIssueNumber(linkedIssue);
					body = body.replaceFirst("\\[" + key + "]\\(.*" + key + "\\)", "#" + ghLinkedIssueNumber);
				}
			}
			infoComment.setBody(body);
			issueService.editComment(repositoryIdProvider, infoComment);
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

		List<JiraFixVersion> fixVersions = JiraFixVersion.sort(jiraIssue.getFields().getFixVersions());
		JiraFixVersion fixVersion = fixVersions.isEmpty() ? null : fixVersions.get(0);
		List<JiraFixVersion> backportVersions = fixVersions.size() > 1 ?
				fixVersions.subList(1, fixVersions.size()) : Collections.emptyList();

		ImportGithubIssue importGithubIssue = new ImportGithubIssue();
		importGithubIssue.setIssue(initGithubIssue(jiraIssue, fixVersion, milestones));
		importGithubIssue.setComments(initComments(jiraIssue));

		ImportGithubIssueResponse importResponse = executeIssueImport(importGithubIssue);

		return new ImportedIssue(jiraIssue, importResponse, backportVersions);

	}

	private GithubIssue initGithubIssue(JiraIssue issue, JiraFixVersion fixVersion, Map<String, Milestone> milestones) {
		Fields fields = issue.getFields();
		boolean closed = fields.getResolution() != null;
		DateTime updated = fields.getUpdated();
		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setTitle(fields.getSummary());

		String migratedLink = issue.getBrowserUrl();
		MarkupEngine engine = markup.engine(issue.getFields().getCreated());
		JiraUser reporter = fields.getReporter();
		String body = "#### **" + engine.link(reporter.getDisplayName(), reporter.getBrowserUrl()) + "** " +
				"opened this issue at **" + engine.link(issue.getKey(), migratedLink) + "**\n";
		if(fields.getDescription() != null) {
			body += "\n" + engine.convert(fields.getDescription());
		}
		ghIssue.setBody(body);
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

		if (fixVersion != null) {
			milestoneHandler.applyVersion(ghIssue, fixVersion.getName(), milestones);
		}

		JiraIssueType issueType = fields.getIssuetype();
		if(issueType != null) {
			Label label = issueTypeLabelMapper.apply(issueType.getName());
			if (label != null) {
				ghIssue.getLabels().add(label.getName());
			}
		}

		List<String> componentNameLabels = fields.getComponents().stream()
				.map(component -> componentLabelMapper.apply(component.getName()))
				.filter(Objects::nonNull)
				.map(Label::getName)
				.collect(Collectors.toList());
		ghIssue.getLabels().addAll(componentNameLabels);

		return ghIssue;
	}

	private List<GithubComment> initComments(JiraIssue issue) {
		Fields fields = issue.getFields();
		MarkupEngine engine = markup.engine(fields.getCreated());
		List<GithubComment> comments = new ArrayList<>();
		GithubComment infoComment = initInfoComment(issue, engine);
		if (infoComment != null) {
			comments.add(infoComment);
		}
		for (JiraComment jiraComment : fields.getComment().getComments()) {
			GithubComment comment = new GithubComment();
			String userUrl = jiraComment.getAuthor().getBrowserUrl();
			String body = "#### **" + engine.link(jiraComment.getAuthor().getDisplayName(), userUrl) + "** commented\n\n";
			body += engine.convert(jiraComment.getBody());
			comment.setBody(body);
			comment.setCreatedAt(jiraComment.getCreated());
			comments.add(comment);
		}
		return comments;
	}

	private GithubComment initInfoComment(JiraIssue issue, MarkupEngine engine) {
		String body = "#### Details from Jira issue\n";
		Fields fields = issue.getFields();
		body += "\nStatus: " + (fields.getStatus() != null ? fields.getStatus().getName() : "none");
		body += "\nResolution: " + (fields.getResolution() != null ? fields.getResolution().getName() : "Unresolved");
		if (!fields.getIssuelinks().isEmpty()) {
			body += fields.getIssuelinks().stream()
					.map(link -> {
						// For now use Jira Browser URL. Later will edit to replace with GH issue numbers.
						if (link.getOutwardIssue() != null) {
							String key = link.getOutwardIssue().getKey();
							return engine.link(key, JiraIssue.getBrowserUrl(jiraConfig.getBaseUrl(), key)) +
									" (" + link.getType().getOutward() + ")";
						}
						else {
							String key = link.getInwardIssue().getKey();
							return engine.link(key, JiraIssue.getBrowserUrl(jiraConfig.getBaseUrl(), key)) +
									" (" + link.getType().getInward() + ")";
						}
					})
					.collect(Collectors.joining(", ", "\nIssue Links: ", ""));
		}
		if (fields.getReferenceUrl() != null) {
			body += "\nReference URL: " + fields.getReferenceUrl();
		}
		if (fields.getPullRequestUrl() != null) {
			body += "\nPull Request URL: " + fields.getPullRequestUrl();
		}
		if (body.isEmpty()) {
			return null;
		}
		GithubComment comment = new GithubComment();
		comment.setBody(body);
		comment.setCreatedAt(fields.getCreated());
		return comment;
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
					return "- **#" + ghIssueNumber + "** - " + summary;
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
				System.out.println("Issue "+ importedIssue.getJiraIssue().getKey()+ " is still pending import, " +
						"waiting "+secondsToWait + " seconds to look up GitHub issue number again");
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
		final List<JiraFixVersion> backportVersions;

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
					System.out.println(method + " " + url.getPath() + " " + request.getHeaders());
				};
				return super.doExecute(url, method, decoratedRequestCallback, responseExtractor);
			} catch(HttpClientErrorException e) {
				HttpHeaders headers = e.getResponseHeaders();
				Assert.notNull(headers, "No headers: " + method + " " + url);
				long sleep;
				if(!"0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
					if(e.getResponseBodyAsString().contains("https://developer.github.com/v3/#abuse-rate-limits")) {
						System.out.println("Received https://developer.github.com/v3/#abuse-rate-limits with no indication of how long to wait. Let's guess & wait "+abuseSleepTimeInSeconds+ " seconds.");
						sleep = TimeUnit.SECONDS.toMillis(abuseSleepTimeInSeconds);
					} else {
						System.out.println(e.getResponseBodyAsString());
						throw e;
					}
				} else {
					System.out.println("Received X-RateLimit-Reset. Waiting to do additional work");
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
			System.out.println(response.getStatusCode() +
					" {X-RateLimit-Remaining:" + response.getHeaders().getFirst("X-RateLimit-Remaining") + "}");
			super.handleResponse(url, method, response);
		}

		private void sleepFor(long sleep) {
			if(sleep < 1) {
				return;
			}
			long endTime = System.currentTimeMillis() + sleep;
			System.out.println("Sleeping until "+ new DateTime(endTime));
			for(long now = System.currentTimeMillis(); now < endTime; now = System.currentTimeMillis()) {
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e1) {
					// Ignore
				}
			}
			System.out.println("Continuing");
		}
	}


	private class ExtendedEgitGitHubClient extends GitHubClient {

		@Override
		protected HttpURLConnection configureRequest(HttpURLConnection request) {
			setOAuth2Token(config.getAccessToken());
			System.out.println(request.getRequestMethod().toUpperCase() + " " + request.getURL().getPath());
			waitForAtLeastOneSecondBetweenRequests();
			HttpURLConnection result = super.configureRequest(request);
			result.setRequestProperty(HEADER_ACCEPT, MediaType.APPLICATION_JSON_VALUE);
			return result;
		}

		@Override
		protected GitHubClient updateRateLimits(HttpURLConnection request) {
			GitHubClient client = super.updateRateLimits(request);
			System.out.println(getHttpStatus(request) + " {X-RateLimit-Remaining:" + getRemainingRequests() + "}");
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
