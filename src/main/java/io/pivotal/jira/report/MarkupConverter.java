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
package io.pivotal.jira.report;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraComment;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.util.MarkdownEngine;


/**
 * Dump all issue descriptions and comments to a file, which can then be grepped
 * to mine for examples of occurrences of specific markup. This is very useful
 * for debugging the Jira markup to Markdown conversion.
 *
 * @author Rossen Stoyanchev
 */
public class MarkupConverter extends BaseReport {


	public static void main(String args[]) throws IOException {

		JiraClient client = initJiraClient();
		JiraConfig config = client.getJiraConfig();

		MarkdownEngine engine = new MarkdownEngine();
		engine.setJiraConfig(config);

		List<JiraIssue> issues = client.findIssues(config.getMigrateJql());
		writeIssuesMarkup(issues, new File("markup-before.txt"), null);
		writeIssuesMarkup(issues, new File("markup-after.txt"), engine);
	}

	private static void writeIssuesMarkup(List<JiraIssue> issues, File file, MarkdownEngine engine) throws IOException {
		try (FileWriter writerSource = new FileWriter(file, false)) {
			for (JiraIssue issue : issues) {
				writerSource.write("\n========== " + issue.getKey() + " ========================================\n");
				JiraIssue.Fields fields = issue.getFields();
				String description = fields.getDescription();
				writerSource.write(description != null ? convertIfNecessary(engine, description) : "\n");
				for (JiraComment comment : fields.getComment().getComments()) {
					writerSource.write("\n------------------------------------------------------------\n");
					String body = comment.getBody();
					writerSource.write(body != null ? convertIfNecessary(engine, body) : "\n");
				}
				writerSource.flush();
			}
		}
	}

	private static String convertIfNecessary(MarkdownEngine engine, String markup) {
		return engine != null && markup != null ? engine.convert(markup) : markup;
	}

}
