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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.migration.FieldValueLabelHandler.FieldType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Configuration for migration of Spring AMQP JIRA.
 */
@Configuration
@ConditionalOnProperty(name = "jira.projectId", havingValue = "AMQP")
public class AmqpMigrationConfig {

	private static final List<String> skipVersions =
			Arrays.asList("Pending Closure", "Waiting for Triage");


	@Bean
	public MilestoneFilter milestoneFilter() {
		return fixVersion -> !skipVersions.contains(fixVersion.getName());
	}

	@Bean
	public LabelHandler labelHandler() {
		FieldValueLabelHandler fieldValueHandler = new FieldValueLabelHandler();
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Admin", "admin", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Build", "build", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Erlang", "erlang", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "RabbitMQ", "rabbitmq", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Test Support", "testing", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "RabbitMQ Junit", "testing", AmqpMigrationConfig::componentLabel);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Samples", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation", "documentation", LabelFactories.TYPE_LABEL);

		// "Build System" - not used

		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Bug", "bug");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Defect", "bug");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "New Feature", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Improvement", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Refactoring", "refactoring");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Task", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Sub-task", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Story", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Epic", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Technical task", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Support", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Backport", "task");
		// "Backport" - ignore

		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Deferred", "declined", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Do", "declined", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Fix", "declined", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Works as Designed", "declined", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Duplicate", "duplicate", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Invalid", "invalid", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Incomplete", "invalid", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Cannot Reproduce", "invalid", AmqpMigrationConfig::statusLabel);
		// "Complete", "Fixed", "Done" - it should be obvious if it has fix version and is closed

		fieldValueHandler.addMapping(FieldType.STATUS, "Waiting for Feedback", "waiting-for-reporter", AmqpMigrationConfig::statusLabel);
		// "Resolved", "Closed -- it should be obvious if issue is closed (distinction not of interest)
		// "Open", "Re Opened" -- it should be obvious from GH timeline
		// "In Progress", "Investigating" -- no value in those, they don't work well

		fieldValueHandler.addMapping(FieldType.VERSION, "Waiting for Triage", "waiting-for-triage", AmqpMigrationConfig::statusLabel);
		fieldValueHandler.addMapping(FieldType.VERSION, "Waiting For Diagnostics", "waiting-for-triage", AmqpMigrationConfig::statusLabel);

		fieldValueHandler.addMapping(FieldType.LABEL, "Regression", "regression", LabelFactories.TYPE_LABEL);


		CompositeLabelHandler handler = new CompositeLabelHandler();
		handler.addLabelHandler(fieldValueHandler);

		handler.addLabelHandler(LabelFactories.STATUS_LABEL.apply("waiting-for-triage"), issue ->
				issue.getFields().getResolution() == null && issue.getFixVersion() == null);

		handler.addLabelHandler(LabelFactories.HAS_LABEL.apply("votes-jira"), issue ->
				issue.getVotes() >= 10);

		handler.addLabelHandler(LabelFactories.HAS_LABEL.apply("backports"), issue ->
				!issue.getBackportVersions().isEmpty());

		handler.addLabelSupersede("type: bug", "type: regression");
		handler.addLabelSupersede("type: task", "type: documentation");
		handler.addLabelSupersede("status: waiting-for-triage", "status: waiting-for-feedback");

		// In Jira users pick the type when opening ticket. It doesn't work that way in GitHub.
		handler.addLabelRemoval("status: waiting-for-triage", label -> label.startsWith("type: "));
		// If it is invalid, the issue type is undefined
		handler.addLabelRemoval("status: invalid", label -> label.startsWith("type: "));
		// Anything declined is not a bug nor regression
		handler.addLabelRemoval("status: declined",  label -> label.equals("type: bug"));
		handler.addLabelRemoval("status: declined",  label -> label.equals("type: regression"));
		// No need to label an issue with bug or regression if it is a duplicate
		handler.addLabelRemoval("status: duplicate", label -> label.equals("type: bug"));
		handler.addLabelRemoval("status: duplicate", label -> label.equals("type: regression"));

		return handler;
	}

	private static Map<String, String> componentLabel(String labelName) {
		Map<String, String> label = LabelFactories.IN_LABEL.apply(labelName);
		label.put("color", "27ddc8");
		return label;
	}

	private static Map<String, String> statusLabel(String labelName) {
		Map<String, String> label = LabelFactories.STATUS_LABEL.apply(labelName);
		label.put("color", "5319e7");
		return label;
	}

	@Bean
	public IssueProcessor issueProcessor() {
		return new CompositeIssueProcessor(new AssigneeDroppingIssueProcessor());
	}


	private static class AssigneeDroppingIssueProcessor implements IssueProcessor {

		private static final String label1 = LabelFactories.STATUS_LABEL.apply("waiting-for-triage").get("name");

		private static final String label2 = LabelFactories.STATUS_LABEL.apply("ideal-for-contribution").get("name");


		@Override
		public void beforeImport(JiraIssue issue, ImportGithubIssue importIssue) {
			JiraFixVersion version = issue.getFixVersion();
			GithubIssue ghIssue = importIssue.getIssue();
			if (version != null && version.getName().contains("Backlog") ||
					ghIssue.getLabels().contains(label1) ||
					ghIssue.getLabels().contains(label2)) {

				ghIssue.setAssignee(null);
			}
		}
	}

}
