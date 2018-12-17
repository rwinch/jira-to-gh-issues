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
package io.pivotal;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.pivotal.FieldValueLabelHandler.FieldType;

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
		fieldValueHandler.addMapping(FieldType.COMPONENT, "documentation", "[Documentation]", LabelFactories.TYPE_LABEL);
		// "[Build]" - not used
		// "[Other]" - bad idea

		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Bug", "bug");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "New Feature", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Improvement", "enhancement");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Refactoring", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Pruning", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Task", "task");
		fieldValueHandler.addMapping(FieldType.ISSUE_TYPE, "Sub-task", "task");
		// "Backport" - from a failed experiment a long time ago

		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Deferred", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Do", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Won't Fix", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Works as Designed", "declined");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Duplicate", "duplicate");
		fieldValueHandler.addMapping(FieldType.RESOLUTION, "Invalid", "invalid");
		// "Complete", "Fixed", "Done" - it should be obvious if it has fix version and is closed
		// "Incomplete", "Cannot Reproduce" - like issue-bot does

		fieldValueHandler.addMapping(FieldType.STATUS, "Waiting for Feedback", "waiting-for-feedback");
		// "Resolved", "Closed -- it should be obvious if issues is closed (distinction not of interest)
		// "Open", "Re Opened" -- it should be obvious from GH timeline
		// "In Progress", "Investigating" -- no value in those, don't work well

		fieldValueHandler.addMapping(FieldType.VERSION, "waiting-for-triage", "Waiting for Triage", LabelFactories.STATUS_LABEL);
		fieldValueHandler.addMapping(FieldType.VERSION, "ideal-for-contribution", "Contributions Welcome", LabelFactories.STATUS_LABEL);

		fieldValueHandler.addMapping(FieldType.LABEL, "regression", "Regression", LabelFactories.TYPE_LABEL);

		CompositeLabelHandler handler = new CompositeLabelHandler();
		handler.addLabelHandler(fieldValueHandler);
		handler.addLabelHandler(new VotesLabelHandler());
		handler.addLabelHandler(new BackportsLabelHandler());
		handler.setLabelPostProcessor(labels -> {
			resolveConflict(labels, "type: bug", "type: regression");
			resolveConflict(labels, "type: task", "type: documentation");
		});
		return handler;
	}

	private void resolveConflict(Set<String> labels, String generalLabel, String specificLabel) {
		if (labels.contains(generalLabel) && labels.contains(specificLabel)) {
			labels.remove(generalLabel);
		}
	}

}
