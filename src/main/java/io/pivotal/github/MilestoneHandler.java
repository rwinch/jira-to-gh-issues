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
package io.pivotal.github;


import java.util.Map;

import io.pivotal.jira.JiraVersion;
import org.eclipse.egit.github.core.Milestone;

import org.springframework.lang.Nullable;


public interface MilestoneHandler {

	/**
	 * How a Jira version is mapped to GH milestone, possibly skipping some.
	 */
	@Nullable
	Milestone mapVersion(JiraVersion version);

	/**
	 * How a Jira version is applied to a GH issue, or alternatively applying a label instead
	 * (e.g. "Waiting for Triage" version to "waiting-for-triage" label).
	 */
	void applyVersion(GithubIssue ghIssue, String fixVersion, Map<String, Milestone> milestones);

}
