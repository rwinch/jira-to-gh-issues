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

public class DefaultMilestoneHandler implements MilestoneHandler {

	@Nullable
	public Milestone mapVersion(JiraVersion version) {
		Milestone milestone = new Milestone();
		milestone.setTitle(version.getName());
		milestone.setState(version.isReleased() ? "closed" : "open");
		if (version.getReleaseDate() != null) {
			milestone.setDueOn(version.getReleaseDate().toDate());
		}
		return milestone;
	}

	@Override
	public void applyVersion(GithubIssue ghIssue, String fixVersion, Map<String, Milestone> milestones) {
		Milestone milestone = milestones.get(fixVersion);
		if (milestone != null) {
			ghIssue.setMilestone(milestone.getNumber());
		}
	}

}
