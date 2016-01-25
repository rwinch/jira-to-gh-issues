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
package io.pivotal.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Rob Winch
 *
 */
public class TextileEngineTests {
	TextileEngine engine;

	@Before
	public void setup() {
		engine = new TextileEngine();
		engine.setJiraBaseUrl("https://jira.spring.io");
	}

	@Test
	public void noChanges() {
		String body = "here";
		assertThat(engine.convert(body)).isEqualTo(body);
	}

	@Test
	public void link() {
		String body = "first\n[text here|https://example.com]\nthere";
		assertThat(engine.convert(body)).isEqualTo("first\n[\"text here\":https://example.com]\nthere");
	}

	@Test
	public void twoLinks() {
		String body =
				" a [fix|https://fisheye.springsource.org/changelog/spring-security?cs=ffe2834f4cd900d99c4a490af62613d087c9aceb] the [Spring Security forums|http://forum.springsource.org/forumdisplay.php?33-Security]";

		assertThat(engine.convert(body)).contains("[\"fix\":https://fisheye.springsource.org/changelog/spring-security?cs=ffe2834f4cd900d99c4a490af62613d087c9aceb]");
	}

	@Test
	public void blockCode() {
		String body = "{code}\nsomething\n{code}";
		assertThat(engine.convert(body)).isEqualTo("\nbc.. \nsomething\n\np. \n");
	}

	@Test
	public void blockCodeJava() {
		String body = "{code:java}\nsomething\n{code}";
		assertThat(engine.convert(body)).isEqualTo("\nbc.. \nsomething\n\np. \n");
	}

	@Test
	public void blockCodeTitle() {
		String body = "{code:title=a.java}\nsomething\n{code}";
		assertThat(engine.convert(body)).isEqualTo("\nbc.. \nsomething\n\np. \n");
	}

	@Test
	public void blockCodeTypeAndTitle() {
		String body = "{code:java|title=a.java}\nsomething\n{code}";
		assertThat(engine.convert(body)).isEqualTo("\nbc.. \nsomething\n\np. \n");
	}

	@Test
	public void blockNoformat() {
		String body = "{noformat}\nsomething\n{noformat}";
		assertThat(engine.convert(body)).isEqualTo("\nbc.. \nsomething\n\np. \n");
	}

	@Test
	public void quote() {
		String body = "{quote}\nsomething\n{quote}";
		assertThat(engine.convert(body)).isEqualTo("\nbc.. \nsomething\n\np. \n");
	}

	@Test
	public void userMention() {
		String body = "[~awilkinson] Thanks for the report. This should now be fixed.";

		assertThat(engine.convert(body)).startsWith("[\"awilkinson\":https://jira.spring.io/secure/ViewProfile.jspa?name=awilkinson]");
	}

	@Test
	public void inlineCode() {
		String body = "The {{AttributesMapper}} methods are excluded on purpose – we normally discourage usage of {{AttributesMapper}} since there is typically no reason for using that rather than the {{ContextMapper}}.\n" +
				"\n" +
				"For the occasion where you need more control and want to access the more generic methods in {{LdapTemplate}}, these are accessible using {{SimpleLdapTemplate#getLdapOperations()}}, completely analogous with {{SimpleJdbcTemplate}} and {{JdbcTemplate}}";

		assertThat(engine.convert(body)).startsWith("The @AttributesMapper@ methods are excluded");
	}

}
