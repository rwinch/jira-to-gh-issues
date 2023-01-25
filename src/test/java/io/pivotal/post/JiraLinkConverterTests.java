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

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Rossen Stoyanchev
 */
public class JiraLinkConverterTests {

	private final Map<String, Integer> issueMappings = new HashMap<>();

	private JiraLinkConverter converter;


	@Before
	public void setUp() {
		issueMappings.put("SPR-14828", 1);
		issueMappings.put("SPR-16422", 2);
		issueMappings.put("SPR-17039", 3);
		issueMappings.put("SPR-15597", 4);
		issueMappings.put("SPR-14544", 5);
		issueMappings.put("SPR-15269", 6);
		this.converter = new JiraLinkConverter("https://jira-stage.spring.io", "SPR", issueMappings, new StringWriter());
	}


	@Test
	public void linksReplaced() {

		String body =
				"**Issue Links:**\n" +
				"- [SPR-14828](https://jira-stage.spring.io/browse/SPR-14828) UriComponentBuilder doesn't ...\n" +
				"- [SPR-16422](https://jira-stage.spring.io/browse/SPR-16422) [docs] Explain ...\n" +
				"- [SPR-17039](https://jira-stage.spring.io/browse/SPR-17039) Support stricter ... (_**\"is superseded by\"**_)";

		assertThat(converter.convert(body)).isEqualTo(
				"**Issue Links:**\n" +
				"- #1 UriComponentBuilder doesn't ...\n" +
				"- #2 [docs] Explain ...\n" +
				"- #3 Support stricter ... (_**\"is superseded by\"**_)\n\n");
	}

	@Test
	public void linkNotReplacedWhenRedirectFalse() {
		String body = "opened **[SPR-16718](https://jira-stage.spring.io/browse/SPR-16718?redirect=false)** and";
		assertThat(converter.convert(body)).isEqualTo(body + "\n");
	}

	@Test
	public void linkNotReplaced() {

		String body = "The commit references [SPR-14828](https://jira-stage.spring.io/browse/SPR-14828" +
				"?focusedCommentId=47792" +
				"&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel" +
				"#comment-47792) which also";

		assertThat(converter.convert(body)).isEqualTo(body + "\n");
	}

	@Test
	public void rawJiraLinks() {
		String body =
				"**[Arend v. Reinersdorff](https://jira-stage.spring.io/secure/ViewProfile.jspa?name=arend.von.reinersdorff)** opened **[SPR-15269](https://jira-stage.spring.io/browse/SPR-15269?redirect=false)** and commented\n" +
						"\nSee the discussion in https://jira-stage.spring.io/browse/SPR-14544 and https://jira-stage.spring.io/browse/SPR-15597. It feels like suffixes are a common thing\n" +
						"(patterns ending '.*', '.txt', '.html') and it could be worth special handling for them. Possibly\n" +
						"a new PathElement subtype specifically for suffixed PathElements (because currently `\"{foo}.*\"`) type patterns are captured as RegexPathElement instances (the least optimal of the PathElement subtypes).";

		assertThat(converter.convert(body)).isEqualTo(
				"**[Arend v. Reinersdorff](https://jira-stage.spring.io/secure/ViewProfile.jspa?name=arend.von.reinersdorff)** opened **[SPR-15269](https://jira-stage.spring.io/browse/SPR-15269?redirect=false)** and commented\n" +
						"\nSee the discussion in #5 and #4. It feels like suffixes are a common thing\n" +
						"(patterns ending '.*', '.txt', '.html') and it could be worth special handling for them. Possibly\n" +
						"a new PathElement subtype specifically for suffixed PathElements (because currently `\"{foo}.*\"`) type patterns are captured as RegexPathElement instances (the least optimal of the PathElement subtypes).\n");
	}

}
