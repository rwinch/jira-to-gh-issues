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
package io.pivotal.post;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.pivotal.util.ProgressTracker;

import org.springframework.http.RequestEntity;

import static org.hamcrest.text.IsEqualCompressingWhiteSpace.equalToCompressingWhiteSpace;

/**
 * @author Rossen Stoyanchev
 */
public class JiraLinkConversionApp extends GitHubBaseApp {


	public static void main(String[] args) throws IOException {

		File mappingsFile = new File("github-issue-mappings.properties");
		Map<String, Integer> issueMappings = loadIssueMappings(mappingsFile);

		File failuresFile = new File("jira-link-conversion-failures.txt");
		try (FileWriter failWriter = new FileWriter(failuresFile, true)) {

			String projectId = initJiraConfig().getProjectId();
			JiraLinkConverter converter = new JiraLinkConverter(jiraBaseUrl, projectId, issueMappings, failWriter);

			ProgressTracker tracker = new ProgressTracker(issueMappings.size(), 4, 200, logger.isDebugEnabled());
			issueMappings.forEach((jiraKey, ghIssueId) -> {
				tracker.updateForIteration();

				Map<String, Object> map = exchange(getIssueRequest(ghIssueId), MAP_TYPE, failWriter, null);
				if (map != null) {
					String descBefore = (String) map.get("body");
					String descAfter = converter.convert(descBefore);
					if (!equalToCompressingWhiteSpace(descBefore).matches(descAfter)) {
						exchange(patchIssueRequest(ghIssueId, descAfter), Void.class, failWriter, null);
					}
				}

				List<Map<String, Object>> body = exchange(getCommentsRequest(ghIssueId), LIST_OF_MAPS_TYPE, failWriter, null);
				if (body != null) {
					body.forEach(commentMap -> {
						Integer commentId = (Integer) commentMap.get("id");
						String commentBefore = (String) commentMap.get("body");
						String commentAfter = converter.convert(commentBefore);
						if (!equalToCompressingWhiteSpace(commentBefore).matches(commentAfter)) {
							exchange(patchCommentRequest(commentId, commentAfter), Void.class, failWriter, null);
						}
					});
				}
			});
			tracker.stopProgress();
		}
	}


	private static RequestEntity<Void> getIssueRequest(Integer ghIssueId) {
		return RequestEntity.get(issueUric.expand(ghIssueId).toUri())
				.accept(APPLICATION_GH_RAW_JSON)
				.header("Authorization", "token " + accessToken)
				.build();
	}

	private static RequestEntity<Map<?, ?>> patchIssueRequest(Integer ghIssueId, String body) {
		return RequestEntity.patch(issueUric.expand(ghIssueId).toUri())
				.accept(APPLICATION_GH_RAW_JSON)
				.header("Authorization", "token " + accessToken)
				.body(Collections.singletonMap("body", body));
	}

	private static RequestEntity<Void> getCommentsRequest(Integer ghIssueId) {
		return RequestEntity.get(commentsUricBuilder.expand(ghIssueId).toUri())
				.accept(APPLICATION_GH_RAW_JSON)
				.header("Authorization", "token " + accessToken)
				.build();
	}

	private static RequestEntity<Map<?, ?>> patchCommentRequest(Integer commentId, String body) {
		return RequestEntity.patch(commentUricBuilder.expand(commentId).toUri())
				.accept(APPLICATION_GH_RAW_JSON)
				.header("Authorization", "token " + accessToken)
				.body(Collections.singletonMap("body", body));
	}

}
