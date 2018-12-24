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
package io.pivotal.jira.report;

import io.pivotal.jira.JiraIssue;


/**
 * Estimate how many backport issues would have to be created if each
 * backport is represented with a separate ticket. The answer also varies
 * depending on which version is chosen to be the milestone for a ticket.
 * For example given fix versions 5.1 RC2, 5.0.8, 4.3.19, picking 5.0.8 as the
 * milestone as opposed to 5.1 RC2 results in a substantially lower number of
 * backport issues (~2500 vs ~1000 for SPR with a total of 17K+ issues).
 * See {@link JiraIssue#initFixAndBackportVersions()}.
 *
 * @author Rossen Stoyanchev
 */
public class BackportCountReport extends BaseReport {


	public static void main(String args[]) {

		long count = getIssuesToMigrate().stream()
				.mapToLong(issue -> issue.getBackportVersions().size())
				.sum();

		System.out.println("Estimated backport issue count: " + count + " \n\n");
	}

}
