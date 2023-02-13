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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * @author Rob Winch
 * @author Artem Bilan
 *
 */
@Component
@ConfigurationProperties(prefix="jira")
@Data
public class JiraConfig {
	/**
	 * The base url of JIRA to use. For example, "https://jira.spring.io"
	 */
	String baseUrl;

	/**
	 * The JIRA project id to migrate. For example, "SEC".
	 */
	String projectId;

	/**
	 * (Optional) the JQL used to query which issues should be migrated. This
	 * defaults to all the issues for the projectId.
	 */
	String migrateJql;

	/**
	 * User id for basic auth: optional (e.g. for access to dev-only comments).
	 */
	String user;

	/**
	 * Password for basic auth: optional.
	 */
	String password;

	public String getMigrateJql() {
		return this.migrateJql == null ? "project = '" + getProjectId() + "' ORDER BY key ASC" : this.migrateJql;
	}

}
