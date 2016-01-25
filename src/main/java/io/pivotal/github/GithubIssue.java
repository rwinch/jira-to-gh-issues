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
package io.pivotal.github;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

/**
 * @author Rob Winch
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class GithubIssue {
	private String title;

	private String body;

	@JsonProperty("created_at")
	@JsonSerialize(using = IsoDateTimeSerializer.class)
	private DateTime createdAt;

	private List<String> labels = new ArrayList<>();

	@JsonProperty("updated_at")
	@JsonSerialize(using = IsoDateTimeSerializer.class)
	private DateTime updatedAt;

	@JsonProperty("closed_at")
	@JsonSerialize(using = IsoDateTimeSerializer.class)
	private DateTime closedAt;

	private boolean closed;

	Integer milestone;

	String assignee;
}
