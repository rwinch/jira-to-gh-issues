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
package io.pivotal;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.pivotal.github.DefaultMilestoneHandler;
import io.pivotal.github.GithubClient;
import io.pivotal.github.GithubIssue;
import io.pivotal.github.LabelMapper;
import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraComponent;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraProject;
import io.pivotal.jira.JiraVersion;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Milestone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * @author Rob Winch
 *
 */
@SpringBootApplication
public class Migrate implements CommandLineRunner {

	@Autowired
	JiraClient jira;

	@Autowired
	GithubClient github;

	@Autowired
	JiraConfig jiraConfig;


	public static void main(String args[]) {
		SpringApplication.run(Migrate.class);
	}


	@Override
	public void run(String... strings) throws Exception {

		github.setMilestoneHandler(new SpringFrameworkMilestoneHandler());
		github.setComponentLabelMapper(new ComponentLabelMapper().ignoreList("[Other]"));
		github.setIssueTypeLabelMapper(new IssueTypeLabelMapper().ignoreList("Backport", "Sub-task"));

		setupRepository();

		JiraProject project = jira.findProject(jiraConfig.getProjectId());
		createMilestones(project);
		createComponentLabels(project);
		createIssueTypeLabels(project);

		createIssues();

	}

	private void setupRepository() throws IOException {
		try {
			// Delete if github.delete-create-repository-slug=true and 0 commits
			github.deleteRepository();
		}
		catch(HttpClientErrorException e) {
			if(e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
				throw e;
			}
		}
		System.out.println("Creating a test repository to place the issues in");
		github.createRepository();
	}

	private void createMilestones(JiraProject project) throws IOException {
		System.out.println("Creating milestones");
		github.createMilestones(project.getVersions());
	}

	private void createComponentLabels(JiraProject project) throws IOException {
		System.out.println("Creating component labels");
		List<JiraComponent> components = project.getComponents();
		github.createComponentLabels(components);
	}

	private void createIssueTypeLabels(JiraProject project) throws IOException {
		System.out.println("Creating issue type labels");
		github.createIssueTypeLabels(project.getIssueTypes());
	}

	private void createIssues() throws IOException, InterruptedException {
		System.out.println("Getting JIRA issues");
		List<JiraIssue> issues = jira.findIssues(jiraConfig.getMigrateJql());
		System.out.println("Found "+issues.size()+ " JIRA issues to migrate");

		System.out.println("Creating issues");
		github.createIssues(issues);
	}


	private static class SpringFrameworkMilestoneHandler extends DefaultMilestoneHandler {

		List<String> skipList = Arrays.asList("Contributions Welcome", "Pending Closure", "Waiting for Triage");


		@Override
		public Milestone mapVersion(JiraVersion version) {
			return !skipList.contains(version.getName()) ? super.mapVersion(version) : null;
		}

		@Override
		public void applyVersion(GithubIssue ghIssue, String fixVersion, Map<String, Milestone> milestones) {
			if (fixVersion.equals("Waiting for Triage")) {
				ghIssue.getLabels().add("status: waiting-for-triage");
			}
			else {
				super.applyVersion(ghIssue, fixVersion, milestones);
			}
		}
	}

	private static class ComponentLabelMapper extends LabelMapper {

		@Override
		protected void processLabel(Label label, String nameValue) {
			label.setName("in: " + nameValue.replaceAll("[ :]", "-").replaceAll("^\\[(.*)\\]$", "$1").toLowerCase());
			label.setColor("008672");
		}
	}

	private static class IssueTypeLabelMapper extends LabelMapper {

		@Override
		protected void processLabel(Label label, String nameValue) {
			label.setName("type: " + nameValue.toLowerCase());
			label.setColor("000000");
		}
	}

}
