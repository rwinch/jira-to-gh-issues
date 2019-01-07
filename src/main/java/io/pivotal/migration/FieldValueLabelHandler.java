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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.pivotal.jira.JiraIssue;
import org.eclipse.egit.github.core.Label;

import org.springframework.util.Assert;

/**
 * LabelHandler that can be configured with mappings from Jira field values to
 * Github Labels.
 *
 * @author Rossen Stoyanchev
 */
public class FieldValueLabelHandler implements LabelHandler {

	/**
	 * Jira field types for which mappings can be registered.
	 */
	public enum FieldType {

		ISSUE_TYPE(LabelFactories.TYPE_LABEL),

		RESOLUTION(LabelFactories.STATUS_LABEL),

		STATUS(LabelFactories.STATUS_LABEL),

		COMPONENT(LabelFactories.IN_LABEL),

		VERSION(null),

		LABEL(null);


		private final Function<String, Label> labelFactory;


		FieldType(Function<String, Label> labelFactory) {
			this.labelFactory = labelFactory;
		}

		public Function<String, Label> getLabelFactory() {
			return labelFactory;
		}

	}

	private final Map<String, Label> mappings = new HashMap<>();


	void addMapping(FieldType fieldType, String fieldValue, String labelName) {
		Assert.notNull(fieldType.getLabelFactory(), "No default label creator for " + fieldType);
		addMapping(fieldType, fieldValue, labelName, fieldType.getLabelFactory());
	}

	void addMapping(FieldType fieldType, String fieldValue, String labelName, Function<String, Label> creator) {
		String key = getKey(fieldType, fieldValue);
		mappings.put(key, creator.apply(labelName));
	}


	private String getKey(FieldType fieldType, String fieldValue) {
		return fieldType + fieldValue.toLowerCase();
	}


	@Override
	public Set<Label> getAllLabels() {
		return new HashSet<>(mappings.values());
	}

	@Override
	public Set<String> getLabelsFor(JiraIssue issue) {
		Set<String> labels = new LinkedHashSet<>();
		JiraIssue.Fields fields = issue.getFields();
		if (fields.getIssuetype() != null) {
			addLabel(labels, getKey(FieldType.ISSUE_TYPE, fields.getIssuetype().getName()));
		}
		if (fields.getResolution() != null) {
			addLabel(labels, getKey(FieldType.RESOLUTION, fields.getResolution().getName()));
		}
		if (fields.getStatus() != null) {
			addLabel(labels, getKey(FieldType.STATUS, fields.getStatus().getName()));
		}
		if (fields.getComponents() != null) {
			fields.getComponents().forEach(component -> {
				addLabel(labels, getKey(FieldType.COMPONENT, component.getName()));
			});
		}
		if (issue.getFixVersion() != null) {
			addLabel(labels, getKey(FieldType.VERSION, issue.getFixVersion().getName()));
		}
		if (fields.getLabels() != null) {
			fields.getLabels().forEach(label -> {
				addLabel(labels, getKey(FieldType.LABEL, label));
			});
		}
		return labels;
	}

	private void addLabel(Set<String> labels, String fieldValue) {
		Label label = mappings.get(fieldValue);
		if (label != null) {
			labels.add(label.getName());
		}
	}

}
