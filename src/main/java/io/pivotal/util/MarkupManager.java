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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraUser;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
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
	 * Configure a user key to JiraUser lookup, in order to allow showing the
	 * user display name as opposed to tje user key in user mentions. This is
	 * invoked at runtime when the users are scraped from Jira issues.
	 */
	public void configureUserLookup(Map<String, JiraUser> userLookup) {
		this.markdown.configureUserLookup(userLookup);
	}

	/**
	 *
	 * @param resource
	 * @throws IOException
	 */
	@Autowired
	public void setUserMentionsToEscape(@Value("classpath:user-mentions-to-escape.txt") Resource resource) throws IOException {
		List<String> userMentions = Files.lines(Paths.get(resource.getFile().getPath()))
				.filter(line -> line.startsWith("mentions="))
				.flatMap(line -> Arrays.stream(line.substring("mentions=".length()).split(",")))
				.collect(Collectors.toList());
		System.out.println("User mentions to be escaped in comments: " + userMentions);
		this.markdown.setUserMentionsToEscape(userMentions);
	}


	public MarkupEngine engine(DateTime date) {
		// Force markdown: it seems to work better currently than it might have originally.
		// See original method used for Spring Security migration below..
		return markdown;
	}

//	public MarkupEngine engine(DateTime date) {
//		return isMarkDown(date) ? markdown : textile;
//	}

	private static boolean isMarkDown(DateTime date) {
		return date.isAfter(DateTime.parse("2009-04-20T19:00:00Z"));
	}
}
