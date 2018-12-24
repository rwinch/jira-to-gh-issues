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

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraIssue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Rossen Stoyanchev
 */
public class MigrationContext {

	private static final Logger logger = LogManager.getLogger(MigrationContext.class);


	private final Writer mappingsWriter;

	private final Writer failuresWriter;

	private final Map<String, Integer> issueMappings = new HashMap<>();

	private int failedImportCount;

	private int backportIssueHolderCount;


	public MigrationContext(Writer mappingsWriter, Writer failuresWriter) {
		this.mappingsWriter = mappingsWriter;
		this.failuresWriter = failuresWriter;
	}


	public void setPreviouslyImportedIssueMappings(Map<String, Integer> issueMappings) {
		this.issueMappings.clear();
		this.issueMappings.putAll(issueMappings);
	}

	public List<JiraIssue> filterRemaingIssuesToImport(List<JiraIssue> issues) {
		return issues.stream()
				.filter(issue -> !issueMappings.containsKey(issue.getKey()))
				.collect(Collectors.toList());
	}

	public void addImportResult(MigrationClient.ImportedIssue imported) {
		JiraIssue jiraIssue = imported.getJiraIssue();
		if (imported.getIssueNumber() != null) {
			if (jiraIssue == null) {
				backportIssueHolderCount++;
				return;
			}
			issueMappings.put(jiraIssue.getKey(), imported.getIssueNumber());
			writeLine(mappingsWriter, jiraIssue.getKey() + ":" + imported.getIssueNumber() + "\n");
		}
		else {
			failedImportCount++;
			String ref = jiraIssue != null ? jiraIssue.getKey() : imported.getMilestone().getTitle() + " backports";
			writeLine(failuresWriter, "=> " + ref + " [" + imported.getFailure() + "]\n");
		}
	}

	public void addFailureMessage(String message) {
		writeLine(failuresWriter, message + "\n");
	}

	private void writeLine(Writer writer, String line) {
		try {
			writer.write(line);
			writer.flush();
		}
		catch (IOException ex) {
			logger.error("Failed to write the below import result due to \"{}\":\n{}", ex.getMessage(), line);
		}
	}

	public int getFailedImportCount() {
		return failedImportCount;
	}

	public Integer getGitHubIssueId(String jiraIssueKey) {
		return issueMappings.get(jiraIssueKey);
	}

	@Override
	public String toString() {
		return this.issueMappings.size() + " imported issues, " +
				this.failedImportCount + " failed imports, " + backportIssueHolderCount + " backported issue holders";
	}

}
