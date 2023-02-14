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
package io.pivotal.pre;

import java.util.stream.Collectors;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraComponent;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssueType;
import io.pivotal.jira.JiraProject;
import io.pivotal.jira.JiraVersion;


/**
 * Prints out the JIRA project's components, versions, and issues types.
 * Useful for creating migration config, equivalent to
 * {@link io.pivotal.migration.SprMigrationConfig} and
 * {@link io.pivotal.migration.SwfMigrationConfig}.
 *
 * @author Rossen Stoyanchev
 * @author Artme Bilan
 */
public class MigrationConfigReport extends BaseApp {


	public static void main(String[] args) {

		JiraConfig config = initJiraConfig();
		JiraClient client = new JiraClient(config);
		JiraProject project = client.findProject(config.getProjectId());

		System.out.println("Components: \n" +
				project.getComponents().stream().map(JiraComponent::getName).collect(Collectors.joining("\n")) +
				"\n");

		System.out.println("Versions: \n" +
				project.getVersions().stream().map(JiraVersion::getName).collect(Collectors.joining("\n")) +
				"\n");

		System.out.println("Issue Types: \n" +
				project.getIssueTypes().stream().map(JiraIssueType::getName).collect(Collectors.joining("\n")) +
				"\n");
	}

}
