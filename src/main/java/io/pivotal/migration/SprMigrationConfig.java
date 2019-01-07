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

import java.util.Arrays;
import java.util.List;

import io.pivotal.github.GithubIssue;
import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.migration.FieldValueLabelHandler.FieldType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Configuration for migration of SPR Jira.
 */
@Configuration
public class SprMigrationConfig {

	private static final List<String> skipVersions =
			Arrays.asList("Contributions Welcome", "Pending Closure", "Waiting for Triage");


	@Bean
	public MilestoneFilter milestoneFilter() {
		return fixVersion -> !skipVersions.contains(fixVersion.getName());
	}

	@Bean
	public LabelHandler labelHandler() {

		FieldValueLabelHandler fieldValueHandler = new FieldValueLabelHandler();
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Caching", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core:AOP", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core:DI", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core:Environment", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Core:SpEL", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "EJB", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "JMX", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Task", "core");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Data", "data");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Data:JDBC", "data");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Data:ORM", "data");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "OXM", "data");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Transaction", "data");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "JMS", "messaging");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Messaging", "messaging");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Test", "test");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Messaging:WebSocket", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Reactive", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Remoting", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Web", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Web:Client", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "Web:Portlet", "web");
		fieldValueHandler.addMapping(FieldType.COMPONENT, "[Documentation]", "documentation", LabelFactories.TYPE_LABEL);
		// "[Build]" - not used
		// "[Other]" - bad idea

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
		// "Complete", "Fixed", "Done" - it should be obvious if it has fix version and is closed
		// "Incomplete", "Cannot Reproduce" - no label at all (like issue-bot)

		fieldValueHandler.addMapping(FieldType.STATUS, "Waiting for Feedback", "waiting-for-feedback");
		// "Resolved", "Closed -- it should be obvious if issue is closed (distinction not of interest)
		// "Open", "Re Opened" -- it should be obvious from GH timeline
		// "In Progress", "Investigating" -- no value in those, they don't work well

		fieldValueHandler.addMapping(FieldType.VERSION, "Waiting for Triage", "waiting-for-triage", LabelFactories.STATUS_LABEL);
		fieldValueHandler.addMapping(FieldType.VERSION, "Contributions Welcome", "ideal-for-contribution", LabelFactories.STATUS_LABEL);

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

		return handler;
	}

	@Bean
	public IssueProcessor issueProcessor() {
		return new CompositeIssueProcessor(new AssigneeDroppingIssueProcessor(), new Spr7640IssueProcessor());
	}


	private static class AssigneeDroppingIssueProcessor implements IssueProcessor {

		private static final String label1 = LabelFactories.STATUS_LABEL.apply("waiting-for-triage").getName();

		private static final String label2 = LabelFactories.STATUS_LABEL.apply("ideal-for-contribution").getName();


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


	/**
	 * The description of SPR-7640 is large enough to cause import failure.
	 */
	private static class Spr7640IssueProcessor implements IssueProcessor {

		@Override
		public void beforeConversion(JiraIssue issue) {
			if (issue.getKey().equals("SPR-7640")) {
				JiraIssue.Fields fields = issue.getFields();
				fields.setDescription(fields.getDescription().substring(0, 1000) + "...");
			}
		}
	}

}
