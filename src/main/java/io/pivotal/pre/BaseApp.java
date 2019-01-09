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
package io.pivotal.pre;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;


/**
 * @author Rossen Stoyanchev
 */
public class BaseApp {


	protected static JiraConfig initJiraConfig() {
		Properties props = loadApplicationProperties();
		JiraConfig config = new JiraConfig();
		config.setBaseUrl(props.getProperty("jira.base-url"));
		config.setProjectId(props.getProperty("jira.projectId"));
		config.setMigrateJql(props.getProperty("jira.migrate-jql"));
		config.setUser(props.getProperty("jira.user"));
		config.setPassword(props.getProperty("jira.password"));
		return config;
	}

	protected static String initGithubRepoSlug() {
		Properties props = loadApplicationProperties();
		return props.getProperty("github.repository-slug");
	}

	private static Properties loadApplicationProperties() {
		try {
			Properties props = new Properties();
			props.putAll(PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties")));
			props.putAll(PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-local.properties")));
			return props;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

}
