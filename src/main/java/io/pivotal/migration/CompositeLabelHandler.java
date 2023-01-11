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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraIssue;


public class CompositeLabelHandler implements LabelHandler {

	private final List<LabelHandler> handlers = new ArrayList<>();

	/** More general label superseded by presence of more specific one (e.g. bug and regression) */
	private final Map<String, String> supersedeMappings = new HashMap<>();

	/** If a given label is present, remove a set of others (e.g. waiting-for-triage and "type:...") */
	private final Map<String, Predicate<String>> removeMappings = new HashMap<>();


	public void addLabelHandler(LabelHandler handler) {
		this.handlers.add(handler);
	}

	/**
	 * @param label label to apply if predicate matches
	 * @param issuePredicate predicate to determine whether to add the label
	 */
	public void addLabelHandler(Map<String, String> label, Predicate<JiraIssue> issuePredicate) {
		this.handlers.add(new PredicateLabelHandler(label, issuePredicate));
	}

	/**
	 * If both labels are present, the second supersedes the first.
	 */
	public void addLabelSupersede(String generalLabel, String specificLabel) {
		this.supersedeMappings.put(generalLabel, specificLabel);
	}

	/**
	 * If the first is present, remove all others matching the predicate.
	 */
	public void addLabelRemoval(String triggerLabel, Predicate<String> labelsToDelete) {
		this.removeMappings.put(triggerLabel, labelsToDelete);
	}


	public Set<Map<String, String>> getAllLabels() {
		return handlers.stream()
				.flatMap(mapper -> mapper.getAllLabels().stream())
				.collect(Collectors.toSet());
	}

	public Set<String> getLabelsFor(JiraIssue jiraIssue) {
		Set<String> labels = handlers.stream()
				.flatMap(mapper -> mapper.getLabelsFor(jiraIssue).stream())
				.collect(Collectors.toSet());
		supersedeMappings.forEach((general, specific) -> {
			if (labels.contains(general) && labels.contains(specific)) {
				labels.remove(general);
			}
		});
		removeMappings.forEach((triggerLabel, predicate) -> {
			if (labels.contains(triggerLabel)) {
				List<String> toDelete = labels.stream().filter(predicate).collect(Collectors.toList());
				labels.removeAll(toDelete);
			}
		});
		return labels;
	}


	private static class PredicateLabelHandler implements LabelHandler {

		private final Map<String, String> label;

		private final Predicate<JiraIssue> issuePredicate;

		private final Set<String> labelSet;


		PredicateLabelHandler(Map<String, String> label, Predicate<JiraIssue> issuePredicate) {
			this.label = label;
			this.issuePredicate = issuePredicate;
			this.labelSet = Collections.singleton(label.get("name"));
		}


		@Override
		public Set<Map<String, String>> getAllLabels() {
			return Collections.singleton(label);
		}

		@Override
		public Set<String> getLabelsFor(JiraIssue issue) {
			return issuePredicate.test(issue) ? labelSet : Collections.emptySet();
		}
	}

}
