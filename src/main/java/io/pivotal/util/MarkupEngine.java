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
package io.pivotal.util;

import java.util.Map;

import io.pivotal.jira.JiraUser;

/**
 * @author Rob Winch
 *
 */
public interface MarkupEngine {

	/**
	 * Configure a user key to JiraUser lookup, in order to allow showing the
	 * user display name as opposed to tje user key in user mentions. This is
	 * invoked at runtime when the users are scraped from Jira issues.
	 */
	default void configureUserLookup(Map<String, JiraUser> userLookup) {
		// no-op
	}

	/**
	 * Format the given description and URL according to the markup.
	 */
	String link(String description, String url);

	/**
	 * Convert the text from JIRA to the target markup.
	 */
	String convert(String text);

}
