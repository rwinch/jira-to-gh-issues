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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.egit.github.core.Label;

/**
 * Function to create a GH label from a Jira field value, including the
 * option to skip over specific field values.
 */
public class LabelMapper implements Function<String, Label> {

	List<String> ignoreList = new ArrayList<>();


	public LabelMapper ignoreList(String... valuesToIgnore) {
		this.ignoreList.addAll(Arrays.stream(valuesToIgnore).map(String::toLowerCase).collect(Collectors.toList()));
		return this;
	}


	@Override
	public Label apply(String value) {
		if (this.ignoreList.contains(value.toLowerCase())) {
			return null;
		}
		Label label = new Label();
		processLabel(label, value);
		return label;
	}

	protected void processLabel(Label label, String nameValue) {
		label.setName(nameValue);
	}

}

