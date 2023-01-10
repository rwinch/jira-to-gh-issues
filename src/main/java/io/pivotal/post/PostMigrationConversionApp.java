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
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.RequestEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

import static org.hamcrest.text.IsEqualCompressingWhiteSpace.equalToCompressingWhiteSpace;

/**
 * Post-migration app making a full pass over all issues and their comments and
 * correcting markup issues, see {@link PostMigrationConverter} for details.
 * <p>Note that the issues addressed by this converter were discovered only after
 * the migration. They can however be done as part of the migration in future
 * migrations, so ideally look to integrate them into
 * {@link io.pivotal.util.MarkdownEngine} since it may take many hours to make
 * a full pass and update all issues/comments.
 * <p>There are some tests. However to run in "controlled mode" an examine every
 * change until you're satisfied, put breakpoints on the lines that update the
 * issue description or a comment body. In the debugger copy the "after" value and
 * then open the issue editor in the browser to preview the change. Or to see the
 * exact before and after difference, copy the "before" value, and write a test:
 * <pre class="code">
 * 	String body = "...";
 * 	assertThat(converter.convert(body)).isEqualTo(body);
 * </pre>
 * @author Rossen Stoyanchev
 */
public class PostMigrationConversionApp extends GitHubBaseApp {

	private static final String FAILURES_FILE_NAME = "post-migration-conversion-failures.txt";


	public static void main(String[] args) throws IOException {

		File failuresFile = new File(FAILURES_FILE_NAME);
		try (FileWriter failWriter = new FileWriter(failuresFile, true)) {

			PostMigrationConverter converter = new PostMigrationConverter(failWriter);

			UriComponentsBuilder uricBuilder = UriComponentsBuilder.newInstance()
					.uriComponents(issuesUric)
					.queryParam("page", "{page}")
					.queryParam("per_page", "100")
					.queryParam("state", "all");

			int page = 1;
			int failCount = 0;
			while (true) {
				RequestEntity<Void> pageRequest = issuesPageRequest(uricBuilder, page);
				List<Map<String, Object>> issues = exchange(pageRequest, LIST_OF_MAPS_TYPE, failWriter, null);
				logger.info("Page " + page);
				if (CollectionUtils.isEmpty(issues)) {
					logger.info("No results, exiting..");
					break;
				}
				AtomicBoolean failed = new AtomicBoolean();
				for (Map<String, Object> map : issues) {
					int updateCount = 0;
					if (map.containsKey("pull_request")) {
						continue;
					}
					Integer ghIssueId = (Integer) map.get("number");
					String descBefore = (String) map.get("body");
					String descAfter = converter.convert(descBefore, failed);
					if (!equalToCompressingWhiteSpace(descBefore).matches(descAfter)) {
						updateCount++;
						exchange(patchIssueRequest(ghIssueId, descAfter), Void.class, failWriter, failed);
						failCount = checkFailures(failCount, failed);
					}
					RequestEntity<Void> commentsRequest = getCommentsRequest(ghIssueId);
					List<Map<String, Object>> body = exchange(commentsRequest, LIST_OF_MAPS_TYPE, failWriter, null);
					if (body != null) {
						for (Map<String, Object> commentMap : body) {
							Integer commentId = (Integer) commentMap.get("id");
							String commentBefore = (String) commentMap.get("body");
							String commentAfter = converter.convert(commentBefore, failed);
							if (!equalToCompressingWhiteSpace(commentBefore).matches(commentAfter)) {
								updateCount++;
								RequestEntity<Map<?, ?>> patchRequest = patchCommentRequest(commentId, commentAfter);
								exchange(patchRequest, Void.class, failWriter, failed);
								failCount = checkFailures(failCount, failed);
							}
						}
					}
					System.out.print(updateCount > 0 ? " " + ghIssueId + " (" + updateCount +
							(failed.get() ? " + " + failCount + " failures" : "") + ") " : ".");
				}
				System.out.println();
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

	private static int checkFailures(int failCount, AtomicBoolean failed) {
		if (failed.get()) {
			failCount++;
			if (failCount > 10) {
				logger.info("More than 10 failures, exiting... (see " + FAILURES_FILE_NAME + ")");
				System.exit(0);
			}
		}
		return failCount;
	}

}
