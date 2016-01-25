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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * @author Rob Winch
 *
 */

@Component
@ConfigurationProperties(prefix="github")
@Data
public class GithubConfig {
	/**
	 * The github repository slug to migrate to. For example, to migrate the
	 * issues to https://github.com/spring-projects/spring-security/issues use
	 * "spring-projects/spring-security". It is a good idea to run the migration
	 * against a test repository first.
	 */
	String repositorySlug;

	/**
	 * The OAuth Access Token used to perform the migration. Visit
	 * https://github.com/settings/tokens
	 *
	 * This will typically go in application-local.properties so you don't accidentally commit it.
	 */
	String accessToken;

	/**
	 * <p>
	 * If set, the migration script will attempt to delete / create a GitHub
	 * repository to migrate the issues to using the {@link #getRepositorySlug()}.
	 * </p>
	 *
	 * <p>
	 * This is useful when testing a
	 * migration to a dummy repository.
	 * </p>
	 */
	boolean deleteCreateRepositorySlug;
}
