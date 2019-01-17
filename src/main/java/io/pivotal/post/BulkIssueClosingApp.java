/*
 * Copyright 2002-2019 the original author or authors.
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
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.RequestEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * App to bulk close tickets on the GitHub side as a post migration task.
 *
 * <p>At present the GitHub REST API did not seem to provide a way to retrieve
 * issues using any custom criteria. Instead, search for tickets in the browser using
 * any advanced filter you want, e.g. "is:issue no:milestone created:<2017-01-01".
 * Then use the browser UI to apply the "status: bulk-closed" (or some such) label,
 * and to remove any other label (e.g. "waiting-for-triage"). Then use this App to
 * bulk close those tickets.
 *
 * <p>Note that GitHub does not seem to like bots applying a comment en masse.
 * Maybe it doesn't like it in general, or may be it doesn't like the same comment
 * being applied many times to different issues, but expect it to return 403 on
 * occasion. Give it some time, anywhere between 15 to 30 or more minutes.
 * It should be safe to then re-run this App, which can figure out what has already
 * been updated.
 *
 * @author Rossen Stoyanchev
 */
public class BulkIssueClosingApp extends GitHubBaseApp {

	private static final String COMMENT_BODY =
			"Bulk closing outdated, unresolved issues. Please, reopen if still relevant.";

	private static final String TARGET_LABEL = "status: bulk-closed";


	public static void main(String[] args) throws IOException {

		File failuresFile = new File("bulk-issue-closing-failures.txt");
		try (FileWriter failWriter = new FileWriter(failuresFile, true)) {

			UriComponentsBuilder uricBuilder = UriComponentsBuilder.newInstance()
					.uriComponents(issuesUric)
					.queryParam("page", "{page}")
					.queryParam("state", "all")
					.queryParam("labels", "{labels}")
					.uriVariables(Collections.singletonMap("labels", TARGET_LABEL));

			int page = 1;
			while (true){
				RequestEntity<Void> pageRequest = issuesPageRequest(uricBuilder, page);
				List<Map<String, Object>> issues = exchange(pageRequest, LIST_OF_MAPS_TYPE, failWriter, null);
				logger.info("Page " + page + ": " + (issues != null ? issues.size() + " issues" : "no results (exiting)"));
				if (issues == null) {
					logger.info("No results, exiting..");
					break;
				}
				if (issues.isEmpty()) {
					logger.info("Done, exiting..");
					break;
				}
				AtomicBoolean failed = new AtomicBoolean();
				for (Map<String, Object> map : issues) {
					Integer ghIssueId = (Integer) map.get("number");
					logger.info("Issue: " + ghIssueId);
					RequestEntity<Void> commentsRequest = getCommentsRequest(ghIssueId);
					List<Map<String, Object>> comments = exchange(commentsRequest, LIST_OF_MAPS_TYPE, failWriter, null);
					if (needsComment(comments)) {
						exchange(addCommentRequest(ghIssueId), Void.class, failWriter, failed);
						if (failed.get()) {
							break;
						}
					}
					String state = (String) map.get("state");
					if (state.equals("open")) {
						exchange(closeIssueRequest(ghIssueId), Void.class, failWriter, null);
					}
				}
				if (failed.get()) {
					logger.info("Detected failure, exiting...");
					break;
				}
				page++;
			}
		}
	}

	private static RequestEntity<Void> issuesPageRequest(UriComponentsBuilder uricBuilder, int page) {
		return RequestEntity.get(uricBuilder.build(String.valueOf(page)))
				.accept(APPLICATION_GH_RAW_JSON)
				.header("Authorization", "token " + accessToken)
				.build();
	}

	private static RequestEntity<Void> getCommentsRequest(Integer ghIssueId) {
		return RequestEntity.get(commentsUricBuilder.expand(ghIssueId).toUri())
				.accept(APPLICATION_GH_RAW_JSON)
				.header("Authorization", "token " + accessToken)
				.build();
	}

	private static RequestEntity<Map<String, String>> addCommentRequest(Integer ghIssueId) {
		return RequestEntity.post(commentsUricBuilder.expand(ghIssueId).toUri())
				.header("Authorization", "token " + accessToken)
				.body(Collections.singletonMap("body", COMMENT_BODY));
	}

	private static RequestEntity<Map<String, String>> closeIssueRequest(Integer ghIssueId) {
		return RequestEntity.patch(issueUric.expand(ghIssueId).toUri())
				.header("Authorization", "token " + accessToken)
				.body(Collections.singletonMap("state", "closed"));
	}

	private static boolean needsComment(List<Map<String, Object>> comments) {
		if (CollectionUtils.isEmpty(comments)) {
			return true;
		}
		String lastComment = (String) comments.get(comments.size() - 1).get("body");
		return !lastComment.contains(COMMENT_BODY);
	}

}
