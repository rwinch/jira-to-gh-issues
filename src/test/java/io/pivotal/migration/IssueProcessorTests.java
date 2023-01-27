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

import java.util.Collections;
import java.util.Map;

import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Rossen Stoyanchev
 */
public class IssueProcessorTests {

	private final IssueProcessor issueProcessor = new SprMigrationConfig().issueProcessor();


	@Test
	public void dropAssigneeForGeneralBacklog() {
		testDropAssigneeForBacklogVersion("General Backlog");
	}

	@Test
	public void dropAssigneeFor5xBacklog() {
		testDropAssigneeForBacklogVersion("t.x Backlog");
	}


	private void testDropAssigneeForBacklogVersion(String version) {
		JiraFixVersion v = new JiraFixVersion();
		v.setName(version);

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setFixVersions(Collections.singletonList(v));
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();

		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setAssignee("jhoeller");

		ImportGithubIssue importIssue = new ImportGithubIssue();
		importIssue.setIssue(ghIssue);

		issueProcessor.beforeImport(jiraIssue, importIssue);

		assertThat(importIssue.getIssue().getAssignee()).isNull();
	}

	@Test
	public void dropAssigneeForWaitingForTriageLabel() {
		testDropAssigneeBasedOnLabel(LabelFactories.STATUS_LABEL.apply("waiting-for-triage"));
	}

	@Test
	public void dropAssigneeForIdealForContributionLabel() {
		testDropAssigneeBasedOnLabel(LabelFactories.STATUS_LABEL.apply("ideal-for-contribution"));
	}

	private void testDropAssigneeBasedOnLabel(Map<String, String> label) {
		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setFixVersions(Collections.emptyList());
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();

		GithubIssue ghIssue = new GithubIssue();
		ghIssue.setAssignee("jhoeller");
		ghIssue.getLabels().add(label.get("name"));

		ImportGithubIssue importIssue = new ImportGithubIssue();
		importIssue.setIssue(ghIssue);

		issueProcessor.beforeImport(jiraIssue, importIssue);

		assertThat((importIssue.getIssue().getAssignee())).isNull();
	}
}
