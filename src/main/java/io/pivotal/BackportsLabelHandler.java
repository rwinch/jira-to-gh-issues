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

import java.util.Collections;
import java.util.Set;

import io.pivotal.jira.JiraIssue;
import org.eclipse.egit.github.core.Label;

/**
 * @author Rossen Stoyanchev
 */
public class BackportsLabelHandler implements LabelHandler {

	private static final Label label = LabelFactories.HAS_LABEL.apply("backports");

	private static final Set<String> labelSet = Collections.singleton(label.getName());


	@Override
	public Set<Label> getAllLabels() {
		return Collections.singleton(label);
	}

	@Override
	public Set<String> getLabelsFor(JiraIssue issue) {
		return !issue.getBackportVersions().isEmpty() ? labelSet : Collections.emptySet();
	}
}
