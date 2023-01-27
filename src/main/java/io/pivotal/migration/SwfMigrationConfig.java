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

import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.migration.FieldValueLabelHandler.FieldType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Configuration for migration of SWF Jira.
 */
@Configuration
@ConditionalOnProperty(name = "jira.projectId", havingValue = "SWF")
public class SwfMigrationConfig {

	private static final List<String> skipVersions =
			Arrays.asList("Pending Closure", "Waiting for Triage");


	@Bean
	public MilestoneFilter milestoneFilter() {
		return fixVersion -> !skipVersions.contains(fixVersion.getName());
	}

	@Bean
	public LabelHandler labelHandler() {

		FieldValueLabelHandler fieldValueHandler = new FieldValueLabelHandler();
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Binding: Attribute Mapping System", "binding");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Binding: Collection Utilities", "binding");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Binding: Expression Language Support", "binding");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Binding: Messages System", "binding");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Binding: Type Conversion System", "binding");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Binding: Validation System", "binding");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Actions", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow Definition Registry", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow Engine: Engine Implementation", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow Engine: Flow Builder", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow Execution Repository", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow Executor", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow System Configuration", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: Flow Test Support", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core: View Selection Rendering", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation: Articles & Whitepapers", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation: Blogs", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation: Reference Manual", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation: Release Announcements", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation: Samples", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Documentation: Web Site", "documentation", LabelFactories.TYPE_LABEL);
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Flow Builder: CRUD", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Flow Builder: Groovy", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Flow Builder: Java", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Flow Model Builder: XML", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Flex / AIR", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: GWT", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: OSGi", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Persistence Support", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Portlet", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Security", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Servlet", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Spring MVC", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Integration: Struts", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "JavaScript", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "JSF", "integration");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Tools", "integration");
		// "Build System" - not used

		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Bug", "bug");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "New Feature", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Improvement", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Refactoring", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Pruning", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Task", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Sub-task", "task");
		// "Backport" - ignore

		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Deferred", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Do", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Fix", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Works as Designed", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Duplicate", "duplicate");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Invalid", "invalid");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Incomplete", "invalid");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Cannot Reproduce", "invalid");
		// "Complete", "Fixed", "Done" - it should be obvious if it has fix version and is closed

		fieldValueHandler.addMapping(FieldType.STATUS, "Waiting for Feedback", "waiting-for-feedback");
		// "Resolved", "Closed -- it should be obvious if issue is closed (distinction not of interest)
		// "Open", "Re Opened" -- it should be obvious from GH timeline
		// "In Progress", "Investigating" -- no value in those, they don't work well

		fieldValueHandler.addMapping(FieldType.VERSION, "Waiting for Triage", "waiting-for-triage", LabelFactories.STATUS_LABEL);

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
