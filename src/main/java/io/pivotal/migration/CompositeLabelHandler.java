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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraIssue;
import org.eclipse.egit.github.core.Label;


public class CompositeLabelHandler implements LabelHandler {

	private final List<LabelHandler> handlers = new ArrayList<>();

	private Consumer<Set<String>> labelPostProcessor;


	public void addLabelHandler(LabelHandler handler) {
		this.handlers.add(handler);
	}

	public void setLabelPostProcessor(Consumer<Set<String>> labelPostProcessor) {
		this.labelPostProcessor = labelPostProcessor;
	}


	public Set<Label> getAllLabels() {
		return handlers.stream()
				.flatMap(mapper -> mapper.getAllLabels().stream()).collect(Collectors.toSet());
	}

	public Set<String> getLabelsFor(JiraIssue jiraIssue) {
		Set<String> labels = handlers.stream()
				.flatMap(mapper -> mapper.getLabelsFor(jiraIssue).stream()).collect(Collectors.toSet());
		if (this.labelPostProcessor != null) {
			this.labelPostProcessor.accept(labels);
		}
		return labels;
	}

}
