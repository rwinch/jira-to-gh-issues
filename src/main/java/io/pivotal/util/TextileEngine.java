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

import io.pivotal.jira.JiraConfig;
import lombok.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * http://redcloth.org/textile
 *
 * @author Rob Winch
 *
 */
@Data
@Component
public class TextileEngine implements MarkupEngine {
	String jiraBaseUrl;

	@Autowired
	public void setJiraConfig(JiraConfig jiraConfig) {
		jiraBaseUrl = jiraConfig.getBaseUrl();
	}

	@Override
	public String link(String text, String href) {
		return "[\"" + text + "\":" + href + "]";
	}

	@Override
	public String convert(String text) {
		if (!StringUtils.hasLength(text)) {
			return "";
		}
		text = blockCode(text);
		text = text.replaceAll("\\[(.+?)\\|(http.*?)\\]","[\"$1\":$2]");
		text = text.replaceAll("\\{\\{(.+?)\\}\\}", "@$1@");
		return text.replaceAll("\\[~([\\w]+)\\]", "[\"$1\":" + jiraBaseUrl + "/secure/ViewProfile.jspa?name=$1]");
	}

	@Override
	public String convertBackportIssueSummary(String text) {
		return text;
	}

	public static String blockCode(String str) {
		String[] parts = str.split("\\{(quote|noformat|code).*?\\}");
		if(parts.length == 1) {
			return str;
		}
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < parts.length; i++) {
			result.append(parts[i]);
			if (i % 2 == 0) {
				result.append("\nbc.. ");
			} else {
				result.append("\np. \n");
			}
		}
		return result.toString();
	}
}
