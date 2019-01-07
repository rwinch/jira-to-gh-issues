/*
 * Copyright 2002-2019 the original author or authors.
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

import io.pivotal.github.ImportGithubIssue;
import io.pivotal.jira.JiraIssue;

/**
 * @author Rossen Stoyanchev
 */
public class CompositeIssueProcessor implements IssueProcessor {

	private final List<IssueProcessor> processors;


	public CompositeIssueProcessor(IssueProcessor... processors) {
		this.processors = Arrays.asList(processors);
	}


	@Override
	public void beforeConversion(JiraIssue issue) {
		processors.forEach(processor -> processor.beforeConversion(issue));
	}

	@Override
	public void beforeImport(JiraIssue jiraIssue, ImportGithubIssue importIssue) {
		processors.forEach(processor -> processor.beforeImport(jiraIssue, importIssue));
	}

}
