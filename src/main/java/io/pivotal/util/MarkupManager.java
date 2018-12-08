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
package io.pivotal.util;

import java.util.Map;

import io.pivotal.jira.JiraUser;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Rob Winch
 *
 */
@Component
public class MarkupManager {

	@Autowired
	MarkdownEngine markdown;

	@Autowired
	TextileEngine textile;


	/**
	 * For user mentions in comments.
	 */
	public void configureUserLookup(Map<String, JiraUser> userLookup) {
		this.markdown.configureUserLookup(userLookup);
		this.textile.configureUserLookup(userLookup);
	}

	public MarkupEngine engine(DateTime date) {
		// Force markdown: it seems to work better currently than it might have originally.
		// Original method below..
		return markdown;
	}

//	public MarkupEngine engine(DateTime date) {
//		return isMarkDown(date) ? markdown : textile;
//	}

	private static boolean isMarkDown(DateTime date) {
		return date.isAfter(DateTime.parse("2009-04-20T19:00:00Z"));
	}
}
