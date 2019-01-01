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

import java.util.HashMap;
import java.util.Map;

import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraUser;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Rob Winch
 *
 */
public class MarkdownEngineTests {
	private MarkdownEngine engine;

	@Before
	public void setup() {
		engine = new MarkdownEngine();
		JiraConfig jiraConfig = new JiraConfig();
		jiraConfig.setBaseUrl("https://jira.spring.io");
		jiraConfig.setProjectId("SPR");
		engine.setJiraConfig(jiraConfig);
	}

	@Test
	public void orderedListWithNestedItemsAndTasks() {
		String body =
				"# (x) Introduce a set of common...\n" +
				"# (x) Special consideration should be taken...\n" +
				"#- The framework provides support for...\n" +
				"#- Component annotations:\n" +
				"#-- {{@Component}}\n" +
				"#-- {{@Service}}\n" +
				"#-- {{@Repository}}\n" +
				"#-- {{@Controller}}\n" +
				"#-- {{@ControllerAdvice}}\n" +
				"#-- {{@RestController}}\n" +
				"#-- {{@Configuration}}\n" +
				"# (/) For each annotation...\n" +
				"#- Ensure that the annotation has...\n" +
				"#- Ensure that the alias name...\n" +
				"#-- For example, something as generic as...\n" +
				"#- Review the Javadoc for...\n" +
				"#- Ensure that all code in the framework...\n";

		assertThat(engine.convert(body)).isEqualTo(
				"1. [ ] Introduce a set of common...\n" +
				"2. [ ] Special consideration should be taken...\n" +
				"   - The framework provides support for...\n" +
				"   - Component annotations:\n" +
				"     - `@Component`\n" +
				"     - `@Service`\n" +
				"     - `@Repository`\n" +
				"     - `@Controller`\n" +
				"     - `@ControllerAdvice`\n" +
				"     - `@RestController`\n" +
				"     - `@Configuration`\n" +
				"3. [x] For each annotation...\n" +
				"   - Ensure that the annotation has...\n" +
				"   - Ensure that the alias name...\n" +
				"     - For example, something as generic as...\n" +
				"   - Review the Javadoc for...\n" +
				"   - Ensure that all code in the framework...\n\n");
	}

	@Test
	public void orderedListNested() {
		String body =
				"# BusBeanFactoryPostProcessor is constructed\n" +
				"# BusBeanFactoryPostProcessor.postProcessBeanFactory is called...\n" +
				"# Bus is constructed\n" +
				"# MyServerManager is constructed\n" +
				"# MyServerManager is injected with the bus, and the serverManager is set on the bus\n" +
				"# MyServerManager.afterPropertiesSet is called, which in effect... As a result:\n" +
				"## MyServerFactoryBean is constructed\n" +
				"## MyServerFactoryBean.afterPropertiesSet() is called:\n" +
				"### MyServer is created and started, creating a new Thread (threadcount=1)\n" +
				"### MyServerFactoryBean inspects the application context for the Bus, ...\n" +
				"### The BindingInfo returned is still null, resulting in a ...\n";

		assertThat(engine.convert(body)).isEqualTo(
				"1. BusBeanFactoryPostProcessor is constructed\n" +
				"2. BusBeanFactoryPostProcessor.postProcessBeanFactory is called...\n" +
				"3. Bus is constructed\n" +
				"4. MyServerManager is constructed\n" +
				"5. MyServerManager is injected with the bus, and the serverManager is set on the bus\n" +
				"6. MyServerManager.afterPropertiesSet is called, which in effect... As a result:\n" +
				"   1. MyServerFactoryBean is constructed\n" +
				"   2. MyServerFactoryBean.afterPropertiesSet() is called:\n" +
				"      1. MyServer is created and started, creating a new Thread (threadcount=1)\n" +
				"      2. MyServerFactoryBean inspects the application context for the Bus, ...\n" +
				"      3. The BindingInfo returned is still null, resulting in a ...\n\n"
		);
	}

	@Test
	public void unorderedListWithTasks() {
		String body =
				"- (/) Refactor the implementation of...\n" +
				"   1. look up _by type_ and _qualifier_ (from {{@Transactional}})\n" +
				"   2. else, look up _by type_ and _explicit name_ (from {{@TransactionConfiguration}})\n" +
				"   3. else, look up single bean _by type_\n" +
				"   4. else, look up _by type_ and _default name_ (from {{@TransactionConfiguration}})\n" +
				"- (/) Update the Javadoc for {{@TransactionalTestExecutionListener}}.\n" +
				"- (/) Update the Javadoc for {{@TransactionConfiguration}}.\n" +
				"- (/) Update the _Testing_ chapter of the Reference Manual accordingly.\n" +
				"- (/) Update the changelog accordingly.\n" +
				"- (/) {{MergedContextConfiguration}} should have ...\n" +
				"- (/) {{MergedContextConfiguration}} should provide ...\n" +
				"-- (/) {{MergedContextConfiguration}} will need ...\n";

		assertThat(engine.convert(body)).isEqualTo(
				"- [x] Refactor the implementation of...\n" +
				"  1. look up _by type_ and _qualifier_ (from `@Transactional`)\n" +
				"  2. else, look up _by type_ and _explicit name_ (from `@TransactionConfiguration`)\n" +
				"  3. else, look up single bean _by type_\n" +
				"  4. else, look up _by type_ and _default name_ (from `@TransactionConfiguration`)\n" +
				"- [x] Update the Javadoc for `@TransactionalTestExecutionListener`.\n" +
				"- [x] Update the Javadoc for `@TransactionConfiguration`.\n" +
				"- [x] Update the _Testing_ chapter of the Reference Manual accordingly.\n" +
				"- [x] Update the changelog accordingly.\n" +
				"- [x] `MergedContextConfiguration` should have ...\n" +
				"- [x] `MergedContextConfiguration` should provide ...\n" +
				"  - [x] `MergedContextConfiguration` will need ...\n\n");
	}

	@Test
	public void unorderedListNested() {
		String body =
				"h3. Proposal\n" +
				"* Create an interface which implements the simplified Errors interface\n" +
				"** Can be instantiated with the target object only\n" +
				"** Doesn't necessary have to store all the rejected properties, tracking if there was any errors might be enough\n" +
				"* Create an abstract class which implements Validator\n" +
				"** Boolean validate(Object target) is implemented here\n";

		assertThat(engine.convert(body)).isEqualTo(
				"### Proposal\n" +
				"\n" +
				"* Create an interface which implements the simplified Errors interface\n" +
				"  * Can be instantiated with the target object only\n" +
				"  * Doesn't necessary have to store all the rejected properties, tracking if there was any errors might be enough\n" +
				"* Create an abstract class which implements Validator\n" +
				"  * Boolean validate(Object target) is implemented here\n\n");
	}

	@Test
	public void h1() {
		String body = "h1. Some Text\nMore";
		assertThat(engine.convert(body)).isEqualTo("# Some Text\n\nMore\n");
	}

	@Test
	public void h2() {
		String body = "h2. Some Text\nh2. Some More Text\nMore";
		assertThat(engine.convert(body)).isEqualTo("## Some Text\n\n## Some More Text\n\nMore\n");
	}

	@Test
	public void h3() {
		String body = "h3. Some Text\nMore";
		assertThat(engine.convert(body)).isEqualTo("### Some Text\n\nMore\n");
	}

	@Test
	public void h4() {
		String body = "h4. Some Text\nMore";
		assertThat(engine.convert(body)).isEqualTo("#### Some Text\n\nMore\n");
	}

	@Test
	public void h5() {
		String body = "h5. Some Text\nMore";
		assertThat(engine.convert(body)).isEqualTo("##### Some Text\n\nMore\n");
	}

	@Test
	public void h6() {
		String body = "h6. Some Text\nMore";
		assertThat(engine.convert(body)).isEqualTo("###### Some Text\n\nMore\n");
	}

	@Test
	public void horizontalRuleMisinterpretation() {
		// In Jira "---" produces em-dash (—) but in Markdown that's a horizontal rule
		String body =
				"Now that I have found the culprit I can use a setup that works.\n\n" +
				"---\n\n" +
				"Having said that...";

		assertThat(engine.convert(body)).isEqualTo(
				"Now that I have found the culprit I can use a setup that works.\n\n" +
				"&mdash;\n\n" +
				"Having said that...\n");
	}

	@Test
	public void twoLinks() {
		String body =
				" a [fix|https://fisheye.springsource.org/changelog/spring-security?cs=ffe2834f4cd900d99c4a490af62613d087c9aceb] the " +
						"[Spring Security forums|http://forum.springsource.org/forumdisplay.php?33-Security]";

		assertThat(engine.convert(body)).contains(
				"[fix](https://fisheye.springsource.org/changelog/spring-security?cs=ffe2834f4cd900d99c4a490af62613d087c9aceb)");
	}

	@Test
	public void tables() {
		String body =
				"h5. Annotations with a {{value}} attribute and other attributes\n" +
				"\n" +
				"|| module                   || Annotation                 || Alias Exists? || Alias Name ||\n" +
				"| {{spring-context}}        | {{@Cacheable}}              | (/)            | {{cacheNames}} |\n" +
				"| {{spring-context}}        | {{@CacheEvict}}             | (/)            | {{cacheNames}} |\n" +
				"| {{spring-web}}            | {{@ResponseStatus}}         | (/)            | {{code}} |\n" +
				"| {{spring-web}}            | {{@SessionAttributes}}      | (/)            | {{names}} |\n";

		assertThat(engine.convert(body)).isEqualTo(
				"##### Annotations with a `value` attribute and other attributes\n" +
				"\n" +
				"|module|Annotation|Alias Exists?|Alias Name|\n" +
				"|:---|:---|:---|:---|\n" +
				"| `spring-context`        | `@Cacheable`              | (/)            | `cacheNames` |\n" +
				"| `spring-context`        | `@CacheEvict`             | (/)            | `cacheNames` |\n" +
				"| `spring-web`            | `@ResponseStatus`         | (/)            | `code` |\n" +
				"| `spring-web`            | `@SessionAttributes`      | (/)            | `names` |\n");
	}

	@Test
	public void userKeyToDisplayName() {
		Map<String, JiraUser> lookup = new HashMap<>();
		lookup.put("juergen.hoeller", user("juergen.hoeller", "Juergen Hoeller"));
		lookup.put("rstoya05-aop", user("rstoya05-aop", "Rossen Stoyanchev"));
		engine.configureUserLookup(lookup);

		String body = "[~juergen.hoeller] abcd [~rstoya05-aop] efg [~unknown] ...";

		assertThat(engine.convert(body)).isEqualTo(
				"[Juergen Hoeller](https://jira.spring.io/secure/ViewProfile.jspa?name=juergen.hoeller) abcd " +
				"[Rossen Stoyanchev](https://jira.spring.io/secure/ViewProfile.jspa?name=rstoya05-aop) efg " +
				"[unknown](https://jira.spring.io/secure/ViewProfile.jspa?name=unknown) ...\n");
	}

	private static JiraUser user(String key, String displayName) {
		JiraUser user = new JiraUser();
		user.setKey(key);
		user.setDisplayName(displayName);
		user.setSelf("https://jira.spring.io");
		return user;
	}

	@Test
	public void escapedGhStyleUserMentions() {
		String body =
				"@Keith: if you have a use case where the lifecycle callbacks are not honored,\n" +
				"please raise a separate JIRA issue with an example that reproduces that.\n" +
				"The one type of dependency injection that @FC types do support is parameter\n" +
				"injection into @Feature methods. This approach is both convenient for\n" +
				"the author of the @Feature method (no need to declare and reference an\n" +
				"@Inject'ed field, and avoids lifecycle issues.\n" +
				"Actually for this to work I would have to either burn the path in the\n" +
				"new meta-annotation or define yet another @RequestMapping(\"foo/{id}\") here again.\n" +
				"@Around(\"this(com.TestAnnotationAdvice$IDog) && execution(public * *(..)) && @annotation(annotation)\")\n" +
				"When a Rest controller's `@RequestBody` params\n" +
				"or annotate your test class with {{@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD))}}.\n" +
				"of bean org.springframework.mock.web.MockHttpServletRequest@c2ff5\n";

		assertThat(engine.convert(body)).isEqualTo(
				"`@Keith`: if you have a use case where the lifecycle callbacks are not honored,\n" +
				"please raise a separate JIRA issue with an example that reproduces that.\n" +
				"The one type of dependency injection that `@FC` types do support is parameter\n" +
				"injection into `@Feature` methods. This approach is both convenient for\n" +
				"the author of the `@Feature` method (no need to declare and reference an\n" +
				"`@Inject`'ed field, and avoids lifecycle issues.\n" +
				"Actually for this to work I would have to either burn the path in the\n" +
				"new meta-annotation or define yet another `@RequestMapping`(\"foo/{id}\") here again.\n" +
				"`@Around`(\"this(com.TestAnnotationAdvice$IDog) && execution(public * *(..)) && `@annotation`(annotation)\")\n" +
				"When a Rest controller's `@RequestBody` params\n" +
				"or annotate your test class with `@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD))`.\n" +
				"of bean org.springframework.mock.web.MockHttpServletRequest@c2ff5\n");
	}

	@Test
	public void jiraIssueKeys() {
		String body =
				"The issue in  SPR-2091 still not resolved.\n" +
				"Please take a look at the commit history for {{spring-framework-issues}} under the {{SPR-8813}} directory.\n" +
				"I've added a new \"SPR-6428\" directory\n" +
				"Attaching [^SPR-7894.patch] which fixes this issue.\n" +
				"jar:file:SPR-13685-0.0.1-SNAPSHOT.jar!/\n" +
				"http://jira.springframework.org/browse/SPR-4324\n" +
				"After the deprecation in Spring 4.2 (SPR-12214), let's\n" +
				"Workaround for <a href=\"https://jira.springsource.org/browse/SPR-9768\">SPR-9768</a>.\n" +
				"please download skeleton-SPR-2583_updated.zip as I uncommented some methods\n" +
				"Attached alternative patch (SPR-5917-1.patch) without interface change\n" +
				"In [SPR-11820] there was a proposal to add chainable Future callbacks\n" +
				"After reading [SPR-10988|https://jira.springsource.org/browse/SPR-10988], I don't see now\n" +
				"[Fixed \\[SPR-11897\\] \\#569|https://github.com/spring-projects/spring-framework/pull/569]\n" +
				"changes can be viewed on my [SPR-9552 branch|https://github.com/cbaldwin74/spring-framework/compare/SPR-9552].\n" +
				"See [https://github.com/rwinch/spring-framework-issues/tree/SPR-12550-Security/SPR-12550] There\n" +
				"I've just fixed the bug in DATAGRAPH-191 and\n";

		assertThat(engine.convert(body)).isEqualTo(
				"The issue in  [SPR-2091](https://jira.spring.io/browse/SPR-2091) still not resolved.\n" +
				"Please take a look at the commit history for `spring-framework-issues` under the `SPR-8813` directory.\n" +
				"I've added a new \"SPR-6428\" directory\n" +
				"Attaching [^SPR-7894.patch] which fixes this issue.\n" +
				"jar:file:SPR-13685-0.0.1-SNAPSHOT.jar!/\n" +
				"http://jira.springframework.org/browse/SPR-4324\n" +
				"After the deprecation in Spring 4.2 ([SPR-12214](https://jira.spring.io/browse/SPR-12214)), let's\n" +
				"Workaround for <a href=\"https://jira.springsource.org/browse/SPR-9768\">SPR-9768</a>.\n" +
				"please download skeleton-SPR-2583_updated.zip as I uncommented some methods\n" +
				"Attached alternative patch (SPR-5917-1.patch) without interface change\n" +
				"In [SPR-11820](https://jira.spring.io/browse/SPR-11820) there was a proposal to add chainable Future callbacks\n" +
				"After reading [SPR-10988](https://jira.springsource.org/browse/SPR-10988), I don't see now\n" +
				"[Fixed \\[SPR-11897\\] \\#569](https://github.com/spring-projects/spring-framework/pull/569)\n" +
				"changes can be viewed on my [SPR-9552 branch](https://github.com/cbaldwin74/spring-framework/compare/SPR-9552).\n" +
				"See https://github.com/rwinch/spring-framework-issues/tree/SPR-12550-Security/SPR-12550 There\n" +
				"I've just fixed the bug in [DATAGRAPH-191](https://jira.spring.io/browse/DATAGRAPH-191) and\n");

	}

	@Test
	public void removeSquareBracketsAroundLinks() {
		String body = "because the default RequestMethodsRequestCondition still checks preflight requests\n\n" +
				"[https://github.com/spring-projects/spring-framework/blob/v5.1.2.RELEASE/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/condition/RequestMethodsRequestCondition.java#L108]\n\n" +
				"Aside: Why are all the condition classes final?\n";

		assertThat(engine.convert(body)).isEqualTo(
				"because the default RequestMethodsRequestCondition still checks preflight requests\n\n" +
						"https://github.com/spring-projects/spring-framework/blob/v5.1.2.RELEASE/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/condition/RequestMethodsRequestCondition.java#L108\n\n" +
						"Aside: Why are all the condition classes final?\n");
	}

	@Test
	public void removeSquareBracketsAroundInlineLinks() {
		String body = "This is an umbrella ticket with sub-tasks:\n" +
				"- [[SPR-17411](https://jira-stage.spring.io/browse/SPR-17411)] `DataBufferUtils`.\n" +
				"- [[SPR-17410](https://jira-stage.spring.io/browse/SPR-17410)] `ReactorServerHttpRequest` to drop the onDiscard hook.\n";

		assertThat(engine.convert(body)).isEqualTo(
				"This is an umbrella ticket with sub-tasks:\n" +
						"- [SPR-17411](https://jira-stage.spring.io/browse/SPR-17411) `DataBufferUtils`.\n" +
						"- [SPR-17410](https://jira-stage.spring.io/browse/SPR-17410) `ReactorServerHttpRequest` to drop the onDiscard hook.\n\n"
		);
	}

	@Test
	public void inlineCode() {
		String body = "The {{AttributesMapper}} methods are excluded on purpose – we normally discourage usage of {{AttributesMapper}} since there is typically no reason for using that rather than the {{ContextMapper}}.\n" +
				"\n" +
				"For the occasion where you need more control and want to access the more generic methods in {{LdapTemplate}}, these are accessible using {{SimpleLdapTemplate#getLdapOperations()}}, completely analogous with {{SimpleJdbcTemplate}} and {{JdbcTemplate}}";

		assertThat(engine.convert(body)).startsWith("The `AttributesMapper` methods are excluded");
	}

	@Test
	public void blockCode() {
		String body = "I've made a custom NtlmProcessingFilterEntryPoint that provides the functionality. Something probably needs to be done in the NtlmProcessingFilter's logon method  for a less hack'ish fix.\n" +
				"{code}\n" +
				"class AcmeNtlmProcessingFilterEntryPoint extends NtlmProcessingFilterEntryPoint {\n" +
				"	public static final String STATE_ATTR = \"SpringSecurityNtlm\";\n" +
				"\n" +
				"\n" +
				"	public void commence(final ServletRequest request, final ServletResponse response, final AuthenticationException authException) throws IOException, ServletException {\n" +
				"		if (authException instanceof BadCredentialsException) {\n" +
				"			((HttpServletRequest) request).getSession().removeAttribute(STATE_ATTR);\n" +
				"			authException = new NtlmBeginHandshakeException();\n" +
				"		}\n" +
				"		super.commence(request, response, authException)\n" +
				"	}\n" +
				"\n" +
				"{code}";

		String convert = engine.convert(body);
		assertThat(convert).endsWith("```\n\n");
	}

	@Test
	public void blockCodeWithTitle() {
		String body =
				"{code:title=web.xml}\n" +
				"    <filter-mapping>\n" +
				"      <filter-name>springSecurityFilterChain</filter-name>\n" +
				"      <url-pattern>/*</url-pattern>\n" +
				"      <dispatcher>REQUEST</dispatcher>\n" +
				"      <dispatcher>FORWARD</dispatcher>\n" +
				"    </filter-mapping>\n{code}";

		String convert = engine.convert(body);
		assertThat(convert).isEqualTo(
				"```\n" +
				"<filter-mapping>\n" +
				"  <filter-name>springSecurityFilterChain</filter-name>\n" +
				"  <url-pattern>/*</url-pattern>\n" +
				"  <dispatcher>REQUEST</dispatcher>\n" +
				"  <dispatcher>FORWARD</dispatcher>\n" +
				"</filter-mapping>\n" +
				"```\n\n");
	}

	@Test
	public void blockCodeWithTypeAndTitle() {
		String body =
				"{code:xml|title=title}\n" +
				"    <filter-mapping>\n" +
				"      <filter-name>springSecurityFilterChain</filter-name>\n" +
				"      <url-pattern>/*</url-pattern>\n" +
				"      <dispatcher>REQUEST</dispatcher>\n" +
				"      <dispatcher>FORWARD</dispatcher>\n" +
				"    </filter-mapping>\n{code}";

		String convert = engine.convert(body);
		assertThat(convert).isEqualTo(
				"```xml\n" +
				"<filter-mapping>\n" +
				"  <filter-name>springSecurityFilterChain</filter-name>\n" +
				"  <url-pattern>/*</url-pattern>\n" +
				"  <dispatcher>REQUEST</dispatcher>\n" +
				"  <dispatcher>FORWARD</dispatcher>\n" +
				"</filter-mapping>\n" +
				"```\n\n");
	}

	@Test
	public void blockCodeNoNewLineAfterOrBefore() {
		String body =
				"{code}" +
				"<filter-mapping>\n" +
				"    <filter-name>springSecurityFilterChain</filter-name>\n" +
				"    <url-pattern>/*</url-pattern>\n" +
				"    <dispatcher>REQUEST</dispatcher>\n" +
				"    <dispatcher>FORWARD</dispatcher>\n" +
				"</filter-mapping>{code}";

		String convert = engine.convert(body);
		assertThat(convert).isEqualTo(
				"```\n" +
				"<filter-mapping>\n" +
				"    <filter-name>springSecurityFilterChain</filter-name>\n" +
				"    <url-pattern>/*</url-pattern>\n" +
				"    <dispatcher>REQUEST</dispatcher>\n" +
				"    <dispatcher>FORWARD</dispatcher>\n" +
				"</filter-mapping>\n" +
				"```\n\n");
	}

	@Test
	public void lineQuote() {
		String body = "bq.Some Text\n bq. More Text\n\t\tbq. And More";
		assertThat(engine.convert(body)).isEqualTo("> Some Text\n> More Text\n> And More\n\n");
	}

	@Test
	public void colorTag1() {
		String body = "I believe {color:red}after{color} should be replaced with " +
				"{color:red}or{color},'cause statement in the parentheses is describing initializing methods.";
		assertThat(engine.convert(body)).isEqualTo("I believe after should be replaced with " +
				"or,'cause statement in the parentheses is describing initializing methods.\n");
	}

	@Test
	public void colorTag2() {
		String body = "{color:#ff0000}assertTrue((sar).hasAlias(\"real_name\", \"alias_b\"));{color} //case 1\n" +
				" sar.registerAlias(\"name\", \"alias_d\");\n" +
				" {color:#ff0000}assertFalse((sar).hasAlias(\"real_name\", \"alias_b\"));{color} //case 2";
		assertThat(engine.convert(body)).isEqualTo(
				"assertTrue((sar).hasAlias(\"real_name\", \"alias_b\")); //case 1\n" +
						"sar.registerAlias(\"name\", \"alias_d\");\n" +
						"assertFalse((sar).hasAlias(\"real_name\", \"alias_b\")); //case 2\n");
	}

	@Test
	public void emphasis() {
		String body = "and *also* in the documentation\n";
		assertThat(engine.convert(body)).isEqualTo("and **also** in the documentation\n");
	}

	@Test
	public void emphasisMediaType() {
		String body =
				"Some request headers(same under both versions):\n" +
				"Accept:application/json, text/javascript, */*; q=0.01\n" +
				"Accept-Encoding:gzip, deflate, br\n" +
				"Accept-Language:zh-CN,zh;q=0.8";
		assertThat(engine.convert(body)).isEqualTo(
				"Some request headers(same under both versions):\n" +
				"Accept:application/json, text/javascript, \\*/\\*; q=0.01\n" +
				"Accept-Encoding:gzip, deflate, br\n" +
				"Accept-Language:zh-CN,zh;q=0.8\n");
	}

	@Test
	public void emphasisMultiline() {
		String body =
				"in my test class i had used\n\n" +
				"package com.htc.springdemos.daos;\n" +
				"import java.io.*;\n" +
				"import org.springframework.beans.factory.BeanFactory;\n" +
				"import org.springframework.beans.factory.xml.XmlBeanFactory;\n" +
				"import org.springframework.core.io.*;\n" +
				"import org.springframework.context.support.*;\n" +
				"import java.util.*;";
		assertThat(engine.convert(body)).isEqualTo(
				"in my test class i had used\n\n" +
				"package com.htc.springdemos.daos;\n" +
				"import java.io.\\*;\n" +
				"import org.springframework.beans.factory.BeanFactory;\n" +
				"import org.springframework.beans.factory.xml.XmlBeanFactory;\n" +
				"import org.springframework.core.io.\\*;\n" +
				"import org.springframework.context.support.\\*;\n" +
				"import java.util.*;\n");
	}

	@Test
	public void emphasisWithUserMention() {
		String body =
				"Manager in my DAO's anymore *even though I nowhere used the @Remote annotation*.";
		assertThat(engine.convert(body)).isEqualTo(
				"Manager in my DAO's anymore **even though I nowhere used the `@Remote` annotation**.\n");
	}

	@Test
	public void emphasisSuppressingHtmlBlocks() {
		String body =
				"the installation of it is preaty clean too:\n" +
				"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<web-app id=\"WebApp_ID\" version=\"2.4\"\n" +
				"\txmlns=\"http://java.sun.com/xml/ns/j2ee\"\n" +
				"\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
				"\txsi:schemaLocation=\"http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd\">\n" +
				"\t<display-name>TO - Novo Site</display-name>\n" +
				"\t<filter>\n" +
				"\t\t<filter-name>requestContext</filter-name>\n" +
				"\t\t<filter-class>\n" +
				"\t\t\torg.springframework.web.filter.RequestContextFilter\n" +
				"\t\t</filter-class>\n" +
				"\t</filter>\n" +
				"\t<filter-mapping>\n" +
				"\t\t<filter-name>requestContext</filter-name>\n" +
				"\t\t<url-pattern>/*</url-pattern>\n" +
				"\t</filter-mapping>\n" +
				"\t<servlet>\n" +
				"\t\t<servlet-name>dispatcher</servlet-name>\n" +
				"\t\t<servlet-class>\n" +
				"\t\t\torg.springframework.web.servlet.DispatcherServlet\n" +
				"\t\t</servlet-class>\n" +
				"\t\t<load-on-startup>1</load-on-startup>\n" +
				"\t</servlet>\n" +
				"\t<servlet-mapping>\n" +
				"\t\t<servlet-name>dispatcher</servlet-name>\n" +
				"\t\t<url-pattern>*.to</url-pattern>\n" +
				"\t</servlet-mapping>\n" +
				"\t<servlet-mapping>\n" +
				"\t\t<servlet-name>dispatcher</servlet-name>\n" +
				"\t\t<url-pattern>/web/*</url-pattern>\n" +
				"\t</servlet-mapping>\n" +
				"</web-app>\n";

		assertThat(engine.convert(body)).isEqualTo(
				"the installation of it is preaty clean too:\n\n" +
				"\\<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"\\<web-app id=\"WebApp_ID\" version=\"2.4\"\n" +
				"xmlns=\"http://java.sun.com/xml/ns/j2ee\"\n" +
				"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
				"xsi:schemaLocation=\"http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd\">\n" +
				"\\<display-name>TO - Novo Site\\</display-name>\n" +
				"\\<filter>\n" +
				"\\<filter-name>requestContext\\</filter-name>\n" +
				"\\<filter-class>\n" +
				"org.springframework.web.filter.RequestContextFilter\n" +
				"\\</filter-class>\n" +
				"\\</filter>\n" +
				"\\<filter-mapping>\n" +
				"\\<filter-name>requestContext\\</filter-name>\n" +
				"\\<url-pattern>/\\*\\</url-pattern>\n" +
				"\\</filter-mapping>\n" +
				"\\<servlet>\n" +
				"\\<servlet-name>dispatcher\\</servlet-name>\n" +
				"\\<servlet-class>\n" +
				"org.springframework.web.servlet.DispatcherServlet\n" +
				"\\</servlet-class>\n" +
				"\\<load-on-startup>1\\</load-on-startup>\n" +
				"\\</servlet>\n" +
				"\\<servlet-mapping>\n" +
				"\\<servlet-name>dispatcher\\</servlet-name>\n" +
				"\\<url-pattern>\\*.to\\</url-pattern>\n" +
				"\\</servlet-mapping>\n" +
				"\\<servlet-mapping>\n" +
				"\\<servlet-name>dispatcher\\</servlet-name>\n" +
				"\\<url-pattern>/web/*\\</url-pattern>\n" +
				"\\</servlet-mapping>\n" +
				"\\</web-app>\n"
		);
	}

	@Test
	@Ignore
	public void emphasisMediaTypeWithinLinkRef() {
		String body =
				"Safari 4:\n\n" +
				"[DEBUG] ... Requested ... [text/plain;q=0.8, image/png, */*;q=0.5] (based on Accept header)";

		assertThat(engine.convert(body)).isEqualTo(
				"Safari 4:\n\n" +
				"[DEBUG] ... Requested ... [text/plain;q=0.8, image/png, \\*/\\*;q=0.5] (based on Accept header)\n");
	}

	@Test
	public void html() {
		String body =
				"Here are my dependancies\n\n" +
				"<dependency>\n" +
				" <groupId>commons-logging</groupId>\n" +
				" <artifactId>commons-logging</artifactId>\n" +
				" <version>1.2</version>\n" +
				" </dependency>\n\n" +
				"<!-- Actual Logging Implementation log4j 2 -->\n" +
				" <dependency>\n" +
				" <groupId>org.apache.logging.log4j</groupId>\n" +
				" <artifactId>log4j-core</artifactId>\n" +
				" <version>2.11.1</version>\n" +
				" </dependency>\n" +
				" <dependency>\n" +
				" <groupId>org.apache.logging.log4j</groupId>\n" +
				" <artifactId>log4j-api</artifactId>\n" +
				" <version>2.11.1</version>\n" +
				" </dependency>\n" +
				" <dependency>\n" +
				" <groupId>org.apache.logging.log4j</groupId>\n" +
				" <artifactId>log4j-web</artifactId>\n" +
				" <version>2.11.1</version>\n" +
				" </dependency>\n";
		assertThat(engine.convert(body)).isEqualTo(
				"Here are my dependancies\n\n" +
				"\\<dependency>\n" +
				"\\<groupId>commons-logging\\</groupId>\n" +
				"\\<artifactId>commons-logging\\</artifactId>\n" +
				"\\<version>1.2\\</version>\n" +
				"\\</dependency>\n" +
				"\\<!-- Actual Logging Implementation log4j 2 -->\n" +
				"\\<dependency>\n" +
				"\\<groupId>org.apache.logging.log4j\\</groupId>\n" +
				"\\<artifactId>log4j-core\\</artifactId>\n" +
				"\\<version>2.11.1\\</version>\n" +
				"\\</dependency>\n" +
				"\\<dependency>\n" +
				"\\<groupId>org.apache.logging.log4j\\</groupId>\n" +
				"\\<artifactId>log4j-api\\</artifactId>\n" +
				"\\<version>2.11.1\\</version>\n" +
				"\\</dependency>\n" +
				"\\<dependency>\n" +
				"\\<groupId>org.apache.logging.log4j\\</groupId>\n" +
				"\\<artifactId>log4j-web\\</artifactId>\n" +
				"\\<version>2.11.1\\</version>\n" +
				"\\</dependency>\n");
	}
}
