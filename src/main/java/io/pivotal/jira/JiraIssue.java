/*
 * Copyright 2002-2016 the original author or authors.
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
package io.pivotal.jira;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.joda.time.DateTime;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Rob Winch
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {

	public static final String FIELD_NAMES = "summary,comment,assignee,components,created,creator," +
			"description,versions,fixVersions,issuetype,reporter,resolution,status,issuelinks," +
			"resolution,updated,parent,subtasks,customfield_10120,customfield_10684";


	String id;

	String key;

	String self;

	Fields fields;

	public String getBrowserUrl() {
		return getBrowserUrl(self, key);
	}

	public static String getBrowserUrl(String baseUrl, String key) {
		return UriComponentsBuilder.fromHttpUrl(baseUrl).replacePath("/browse/").path(key)
				.queryParam("redirect", "false").toUriString();
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Fields {
		String summary;
		String description;
		JiraIssueType issuetype;
		DateTime created;
		DateTime updated;
		JiraCommentPage comment;
		List<JiraComponent> components;
		List<JiraVersion> versions;
		List<JiraFixVersion> fixVersions;
		JiraStatus status;
		JiraResolution resolution;
		JiraUser reporter;
		JiraUser assignee;
		List<IssueLink> issuelinks;
		JiraIssue parent;
		List<JiraIssue> subtasks;
		@JsonProperty("customfield_10120")
		String referenceUrl;
		@JsonProperty("customfield_10684")
		String pullRequestUrl;
	}

}
