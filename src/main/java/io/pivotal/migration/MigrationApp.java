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
package io.pivotal.migration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import io.pivotal.jira.JiraClient;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraIssue;
import io.pivotal.jira.JiraProject;
import io.pivotal.jira.report.MarkupConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;

/**
 * @author Rob Winch
 * @see MarkupConverter
 */
@SpringBootApplication(scanBasePackages = "io.pivotal")
public class MigrationApp implements CommandLineRunner {

	private static final Logger logger = LogManager.getLogger(MigrationApp.class);


	@Autowired
	JiraClient jira;

	@Autowired
	MigrationClient github;

	@Autowired
	JiraConfig jiraConfig;


	public static void main(String args[]) {
		SpringApplication.run(MigrationApp.class);
	}


	@Override
	public void run(String... strings) throws Exception {

		File mappingsFile = new File("github-issue-mappings.properties");
		File failuresFile = new File("github-migration-failures.txt");

		try (FileWriter mappingsWriter = new FileWriter(mappingsFile, true);
			 FileWriter failuresWriter = new FileWriter(failuresFile, true)) {

			String startTime = DateTimeFormat.forStyle("ML").print(DateTime.now());
			failuresWriter.write("==================================\n" + startTime + "\n");
			failuresWriter.flush();

			Map<String, Integer> issueMappings = loadIssueMappings(mappingsFile);
			MigrationContext context = new MigrationContext(mappingsWriter, failuresWriter);
			context.setPreviouslyImportedIssueMappings(issueMappings);

			try {
				// Delete if github.delete-create-repository-slug=true AND 0 commits
				if (github.deleteRepository()) {
					Assert.isTrue(issueMappings.isEmpty(),
							"Repository was deleted but github-issue-mappings.properties has content." +
									"Please delete the file, or save the content elsewhere and then delete.");
				}
			}
			catch (HttpClientErrorException ex) {
				if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
					throw ex;
				}
			}

			github.createRepository();

			JiraProject project = jira.findProject(jiraConfig.getProjectId());
			github.createMilestones(project.getVersions());

			github.createLabels();

			String migrateJql = jiraConfig.getMigrateJql();
			List<JiraIssue> issues = jira.findIssuesVotesAndCommits(migrateJql, context::filterRemaingIssuesToImport);

			List<String> restrictedIssueKeys = issues.stream()
					.filter(issue -> !issue.getFields().isPublic())
					.map(JiraIssue::getKey).collect(Collectors.toList());

			List<JiraIssue> publicIssues = issues.stream()
					.filter(issue -> issue.getFields().isPublic())
					.collect(Collectors.toList());

			github.createIssues(publicIssues, restrictedIssueKeys, context);

			logger.info("Migration run completed: " + context);
		}

		System.exit(0);
	}

	private static Map<String, Integer> loadIssueMappings(File mappingsFile) throws IOException {
		Properties props = new Properties();
		props.load(new FileInputStream(mappingsFile));
		Map<String, Integer> result = new HashMap<>();
		props.stringPropertyNames().forEach(name -> result.put(name, Integer.valueOf(props.getProperty(name))));
		return result;
	}

}
