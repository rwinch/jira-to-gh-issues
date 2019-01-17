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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import io.pivotal.jira.JiraConfig;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;


/**
 * @author Rossen Stoyanchev
 */
public class BaseApp {

	protected static final Properties props = new Properties();

	static {
		try {
			props.putAll(PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties")));
			props.putAll(PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-local.properties")));
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}


	protected static JiraConfig initJiraConfig() {
		JiraConfig config = new JiraConfig();
		config.setBaseUrl(props.getProperty("jira.base-url"));
		config.setProjectId(props.getProperty("jira.projectId"));
		config.setMigrateJql(props.getProperty("jira.migrate-jql"));
		config.setUser(props.getProperty("jira.user"));
		config.setPassword(props.getProperty("jira.password"));
		return config;
	}

	protected static Map<String, Integer> loadIssueMappings(File mappingsFile) throws IOException {
		Properties props = new Properties();
		props.load(new FileInputStream(mappingsFile));
		Map<String, Integer> result = new HashMap<>();
		props.stringPropertyNames().forEach(name -> result.put(name, Integer.valueOf(props.getProperty(name))));
		return result;
	}

}
