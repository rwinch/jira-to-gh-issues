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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.pivotal.jira.JiraComponent;
import io.pivotal.jira.JiraFixVersion;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraIssueType;
import io.pivotal.jira.JiraResolution;
import io.pivotal.jira.JiraStatus;
import org.junit.Test;

import static org.junit.Assert.*;


public class LabelHandlerTests {

	private final LabelHandler labelHandler = new SprMigrationConfig().labelHandler();


	@Test
	public void mapping() {

		JiraComponent component = new JiraComponent();
		component.setName("CaCHinG");

		JiraIssueType issueType = new JiraIssueType();
		issueType.setName("ReFACTorinG");

		JiraResolution resolution = new JiraResolution();
		resolution.setName("WoRkS AS dEsiGned");

		JiraStatus status = new JiraStatus();
		status.setName("WAITing for feedBaCK");

		JiraFixVersion fixVersion = new JiraFixVersion();
		fixVersion.setName("waiting for triage");

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setComponents(Collections.singletonList(component));
		fields.setIssuetype(issueType);
		fields.setResolution(resolution);
		fields.setStatus(status);
		fields.setFixVersions(Collections.singletonList(fixVersion));
		fields.setLabels(Collections.singletonList("RegreSSion"));
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();
		Set<String> labels = labelHandler.getLabelsFor(jiraIssue);

		assertEquals(new HashSet<>(Arrays.asList("in: core", "type: task", "status: declined",
				"status: waiting-for-feedback", "status: waiting-for-triage", "type: regression")), labels);
	}

	@Test
	public void documentationVsTask() {

		JiraIssueType issueType = new JiraIssueType();
		issueType.setName("Task");

		JiraComponent component = new JiraComponent();
		component.setName("[Documentation]");

		JiraFixVersion v = new JiraFixVersion();
		v.setName("5.0.9");

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setIssuetype(issueType);
		fields.setComponents(Collections.singletonList(component));
		fields.setFixVersions(Collections.singletonList(v));
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();
		Set<String> labels = labelHandler.getLabelsFor(jiraIssue);

		assertEquals(Collections.singleton("type: documentation"), labels);
	}

	@Test
	public void regressionVsBug() {

		JiraIssueType issueType = new JiraIssueType();
		issueType.setName("Bug");

		JiraFixVersion v = new JiraFixVersion();
		v.setName("5.0.9");

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setIssuetype(issueType);
		fields.setLabels(Collections.singletonList("regression"));
		fields.setFixVersions(Collections.singletonList(v));
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();
		Set<String> labels = labelHandler.getLabelsFor(jiraIssue);

		assertEquals(Collections.singleton("type: regression"), labels);
	}

	@Test
	public void noFixVersionLabelHandler() {

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setFixVersions(Collections.emptyList());
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();

		assertEquals(Collections.singleton("status: waiting-for-triage"), labelHandler.getLabelsFor(jiraIssue));
	}

	@Test
	public void votesLabelHandler() {

		JiraFixVersion v = new JiraFixVersion();
		v.setName("5.0.9");

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setFixVersions(Collections.singletonList(v));
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();

		jiraIssue.setVotes(9);
		assertEquals(Collections.emptySet(), labelHandler.getLabelsFor(jiraIssue));

		jiraIssue.setVotes(10);
		assertEquals(Collections.singleton("has: votes-jira"), labelHandler.getLabelsFor(jiraIssue));
	}

	@Test
	public void backportsLabelHandler() {

		JiraFixVersion v1 = new JiraFixVersion();
		v1.setName("5.1 RC2");
		JiraFixVersion v2 = new JiraFixVersion();
		v2.setName("5.0.9");
		JiraFixVersion v3 = new JiraFixVersion();
		v3.setName("4.3.19");

		JiraIssue.Fields fields = new JiraIssue.Fields();
		fields.setFixVersions(Arrays.asList(v1, v2, v3));
		fields.setSubtasks(Collections.emptyList());

		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setFields(fields);
		jiraIssue.initFixAndBackportVersions();

		assertEquals(Collections.singleton("has: backports"), labelHandler.getLabelsFor(jiraIssue));
	}

}
