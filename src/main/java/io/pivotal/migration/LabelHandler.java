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


import java.util.Set;

import io.pivotal.jira.JiraIssue;
import org.eclipse.egit.github.core.Label;


/**
 * Assist with the creation of GitHub labels in the beginning of the migration,
 * and later with deciding which labels to apply to migrated issues.
 *
 * @author Rossen Stoyanchev
 */
public interface LabelHandler {

	/**
	 * Return all labels that this handler may apply to an issue,
	 * so those may be pre-created.
	 */
	Set<Label> getAllLabels();

	/**
	 * Map a {@link JiraIssue} to a set of applicable labels.
	 */
	Set<String> getLabelsFor(JiraIssue issue);



}
