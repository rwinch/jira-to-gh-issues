/*
 * Copyright 2002-2019 the original author or authors.
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

import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraIssue;

/**
 * Handler for applying custom updates to an issue about to be imported.
 *
 * @author Rossen Stoyanchev
 */
public interface IssueProcessor {

	/**
	 * Pre-process the Jira issue before its data is converted to a GitHub issue.
	 * @param jiraIssue the Jira issue
	 */
	default void beforeConversion(JiraIssue jiraIssue) {
	}

	/**
	 * Last chance to examine the issue, and the converted data that's about to
	 * be imported and to apply some project-specific customizations.
	 * @param jiraIssue the source Jira issue
	 * @param importIssue the GitHub issue import data
	 */
	default void beforeImport(JiraIssue jiraIssue, ImportGithubIssue importIssue) {
	}

}
