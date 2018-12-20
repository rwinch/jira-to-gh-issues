/*
 * Copyright 2002-2018 the original author or authors.
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

import java.io.StringWriter;
import java.io.Writer;

import io.pivotal.jira.JiraIssue;
import org.eclipse.egit.github.core.Milestone;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Rossen Stoyanchev
 */
public class MigrationContextTests {

	private final Writer mappingsWriter = new StringWriter();
	private final Writer failuresWriter = new StringWriter();
	private final MigrationContext context = new MigrationContext(mappingsWriter, failuresWriter);


	@Test
	public void jiraIssueImportResult() {
		context.addImportResult(jiraIssueImport("SPR-1234", 1300, null));
		assertThat(mappingsWriter.toString()).isEqualTo("SPR-1234:1300\n");
		assertThat(context.getGitHubIssueId("SPR-1234")).isEqualTo(1300);
		assertThat(failuresWriter.toString()).isEmpty();
	}

	@Test
	public void backportIssueImportResult() {
		Milestone milestone = new Milestone();
		milestone.setTitle("4.3.19");
		context.addImportResult(backportIssueHolderImport(milestone, 1300, null));
		assertThat(mappingsWriter.toString()).isEmpty();
		assertThat(failuresWriter.toString()).isEmpty();
		assertThat(context.toString()).isEqualTo("0 imported issues, 0 failed imports, 1 backported issue holders");
	}

	@Test
	public void jiraIssueFailedImport() {
		context.addImportResult(jiraIssueImport("SPR-1234", null, "Failure description"));
		assertThat(mappingsWriter.toString()).isEmpty();
		assertThat(context.getGitHubIssueId("SPR-1234")).isNull();
		assertThat(context.getFailedImportCount()).isEqualTo(1);
		assertThat(failuresWriter.toString()).isEqualTo("=> SPR-1234 [Failure description]\n");
	}

	@Test
	public void backportIssueFailedImport() {
		Milestone milestone = new Milestone();
		milestone.setTitle("4.3.19");
		context.addImportResult(backportIssueHolderImport(milestone, null, "Failure description"));
		assertThat(mappingsWriter.toString()).isEmpty();
		assertThat(context.getFailedImportCount()).isEqualTo(1);
		assertThat(failuresWriter.toString()).isEqualTo("=> 4.3.19 backports [Failure description]\n");
		assertThat(context.toString()).isEqualTo("0 imported issues, 1 failed imports, 0 backported issue holders");
	}

	private static MigrationClient.ImportedIssue jiraIssueImport(String jiraKey, Integer ghIssueId, String failure) {
		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setKey(jiraKey);
		MigrationClient.ImportedIssue imported = new MigrationClient.ImportedIssue(jiraIssue, null, null);
		imported.setIssueNumber(ghIssueId);
		imported.setFailure(failure);
		return imported;
	}

	private MigrationClient.ImportedIssue backportIssueHolderImport(Milestone milestone, Integer ghIssueId, String failure) {
		MigrationClient.ImportedIssue imported = new MigrationClient.ImportedIssue(null, milestone, null);
		imported.setIssueNumber(ghIssueId);
		imported.setFailure(failure);
		return imported;
	}

}
