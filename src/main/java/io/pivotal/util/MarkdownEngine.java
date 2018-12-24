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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.VisitHandler;
import com.vladsch.flexmark.ast.Visitor;
import com.vladsch.flexmark.formatter.internal.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.sequence.CharSubSequence;
import io.pivotal.jira.JiraConfig;
import io.pivotal.jira.JiraUser;
import lombok.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Rob Winch
 * @author Rossen Stoyanchev
 */
@Data
@Component
public class MarkdownEngine implements MarkupEngine {

	private static final Pattern jiraMentionPattern = Pattern.compile("\\[~([^]]+)]");


	String jiraBaseUrl;

	private Map<String, JiraUser> userLookup = new HashMap<>();

	// Flexmark Parser/Formatter/NodeVisitor to further manipulate the converted markdown content
	private final Parser parser = Parser.builder().build();
	private final Formatter formatter = Formatter.builder().build();
	private NodeVisitor visitor;


	@Autowired
	public void setJiraConfig(JiraConfig jiraConfig) {
		this.jiraBaseUrl = jiraConfig.getBaseUrl();
		this.visitor = new MigrationNodeVisitor(jiraConfig);
	}

	@Override
	public void configureUserLookup(Map<String, JiraUser> userLookup) {
		this.userLookup.putAll(userLookup);
	}

	@Override
	public String link(String text, String href) {
		return "[" + text + "](" + href + ")";
	}

	@Override
	public String convert(String text) {
		Assert.notNull(this.visitor, "NodeVisitor is not initialized. Was JiraConfig not autowired?");
		if (!StringUtils.hasLength(text)) {
			return "";
		}
		text = header(text);
		text = text.replaceAll("\\{(code|noformat)(:(\\w+))?(?:(:|\\|)\\w+=.+?)*\\}", "```$3 ");
		text = text.replaceAll("(```\\w*) (.+)", "$1\n$2");
		text = text.replaceAll("(.)(```) ", "$1\n$2");
		text = text.replaceAll("\\{(color)(:((#)?\\w+))?(?:(:|\\|)\\w+=.+?)*\\}", "");
		text = quote(text); // quote blocks
		text = text.replaceAll("(?m)^[ \\t]*bq\\.", "> "); // single line quotes
		text = text.replaceAll("\\[(.+?)\\|(http.*?)\\]", "[$1]($2)"); // convert links
		text = text.replaceAll("\\{\\{(.+?)\\}\\}", "`$1`"); // inline code
		text = replaceUserKeyWithDisplayNameInJiraUserMentions(text);

		// TODO:
		// Ordered lists should be handled from a Flexmark Text Visitor, i.e. to avoid changing code blocks
		// Also take into account nested lists, i.e. ##, #*
		// text = text.replaceAll("(?m)^[ \\t]*# ", "- ");   // ordered

		// Formatting issues: SPR-5079, SPR-7655, SPR-11393 (table, nested bullets) | SPR-1987

		// TODO:
		// Unordered lists mess with "*" in pasted Javadoc so brute force replacement is bad.
		// Experiment with using Flexmark Text Visitor, i.e. anything outside code blocks.
		// text = text.replaceAll("(?m)^[ \\t]*\\* ", "- "); // unordered

		// Apply markdown parser/formatter
		Node node = parser.parse(text);
		visitor.visit(node);
		text = formatter.render(node);

		return text;
	}

	private String replaceUserKeyWithDisplayNameInJiraUserMentions(String text) {
		Matcher matcher = jiraMentionPattern.matcher(text);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String key = matcher.group(1);
			JiraUser user = this.userLookup.computeIfAbsent(key, k -> {
				JiraUser u = new JiraUser();
				u.setKey(k);
				u.setDisplayName(k);
				u.setSelf(this.jiraBaseUrl);
				return u;
			});
			matcher.appendReplacement(sb, "[" + user.getDisplayName() + "](" + user.getBrowserUrl() + ")");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String header(String text) {
		text = text.replaceAll("(?m)^h1. ", "# ");
		text = text.replaceAll("(?m)^h2. ", "## ");
		text = text.replaceAll("(?m)^h3. ", "### ");
		text = text.replaceAll("(?m)^h4. ", "#### ");
		text = text.replaceAll("(?m)^h5. ", "##### ");
		text = text.replaceAll("(?m)^h6. ", "###### ");
		return text;
	}

	private static String quote(String str) {
		String[] parts = str.split("\\{quote\\}");

		for (int i = 1; i < parts.length; i += 2) {
			parts[i] = "\n > " + parts[i].replaceAll("\n", "\n> ");
		}
		return StringUtils.arrayToDelimitedString(parts, "");
	}


	private static class MigrationNodeVisitor extends NodeVisitor {

		private static final String jiraProjectIds =
				"(GREENHOUSE|IMPALA|GRADLE|SHDPADMIN|AMQP|AMQPNET|BATCH|BATCHADM|FLEX|DATAAERO|DATACOL|DATACMNS|DATACOUCH|DATAES|DATACASS|DATASOLR|SGF|DATAGEODE|DATAHB|DATAJDBC|DATAJPA|DATAKV|DATALDAP|DATAMAP|DATAMONGO|DATAGRAPH|DATAREDIS|DATAREST|DATARIAK|ANDROID|SHDP|SPR|SGFNET|IDE|INT|INTEXT|INTDSLGROOVY|INTROO|INTSAMPLES|INTSCALA|INTTEMPLATES|SJC|LDAP|SMA|MOBILE|MOD|OSGI|SPS|RCP|ROO|ROOFLEX|SCALA|SEC|SECOAUTH|SHL|SLICE|SOCIAL|SOCIALFB|SOCIALGH|SOCIALLI|SOCIALTI|SOCIALTW|STS|SWF|SWS|XD|SPRNET|SPRNETCODECONFIG|SPRNETREST|SPRNETSOCIAL|SPRNETSOCIALDB|SPRNETSOCIALFB|SPRNETSOCIALLI|SPRNETSOCIALTW|SPRNETVSADDIN|SESPRINGACTIONSCRIPTAS|SEBLOB|SECOUCHDB|SEDBFONET|SEDBFO|SE|SEBATCHNET|SECONFIGNET|SENMSNET|SERICHCLIENTNET|SETHREADNET|SESIA|SESPRINGINTEGRATIONNET|SEJCR|SESPRINGPYTHONPY|SES|SESQLJ|SESURF|SEWORKFLOW|SEWPFNET)";

		private static final Pattern jiraKeyPattern = Pattern.compile("(" + jiraProjectIds + "-[0-9]{1,5}+)");


		MigrationNodeVisitor(JiraConfig jiraConfig) {
			super(initVisitHandlers(jiraConfig));
		}

		private static List<VisitHandler<?>> initVisitHandlers(JiraConfig jiraConfig) {
			String baseIssueUrl = jiraConfig.getBaseUrl() + "/browse/";
			GitHubUserMentionVisitor gitHubMentionVisitor = new GitHubUserMentionVisitor();
			JiraIssueKeyVisitor issueKeyVisitor = new JiraIssueKeyVisitor(jiraKeyPattern, baseIssueUrl);
			return Arrays.asList(
					new VisitHandler<>(Text.class, text -> {
						gitHubMentionVisitor.visit(text);
						issueKeyVisitor.visit(text);
					}),
					new VisitHandler<>(LinkRef.class, new ReferenceStyleLinkVisitor(jiraKeyPattern, baseIssueUrl)),
					new VisitHandler<>(Link.class, new InlineLinkVisitor()));
		}
	}


	/**
	 * Escape "@"-prefixed content to avoid incidental GitHub user mentions.
	 * Note that it's okay if we escape inside an already escaped sequence, as Flexmark
	 * seems to do the right thing and drop the added "`".
	 */
	private static class GitHubUserMentionVisitor implements Visitor<Text> {

		private static final Pattern ghMentionPattern = Pattern.compile("(^|[^\\w])(@[\\w-]+)");

		@Override
		public void visit(Text textNode) {
			String result = ghMentionPattern.matcher(textNode.getChars()).replaceAll("$1`$2`");
			textNode.setChars(CharSubSequence.of(result));
		}
	}

	/**
	 * Replace Jira issue keys which would be formatted as links in Jira.
	 * However, it's not a simple replacement of all occurrences.
	 * See tests for details.
	 */
	private static class JiraIssueKeyVisitor implements Visitor<Text> {

		/**
		 * Derived by using {@link io.pivotal.jira.report.MarkupConverter} to dump
		 * descriptions and comments for all issues and grepping for examples.
		 */
		private static final List<Character> excludedCharsBeforeJiraIssueKey =
				Arrays.asList('/', '-', ':', '^', '@', '\\', '=', '"', '\'', '`');


		private final Pattern jiraIssueKeyPattern;

		private final String issueBaseUrl;


		JiraIssueKeyVisitor(Pattern jiraIssueKeyPattern, String issueBaseUrl) {
			this.jiraIssueKeyPattern = jiraIssueKeyPattern;
			this.issueBaseUrl = issueBaseUrl;
		}

		@Override
		public void visit(Text text) {
			Matcher matcher = jiraIssueKeyPattern.matcher(text.getChars());
			StringBuffer sb = null;
			while (matcher.find()) {
				String key = matcher.group(1);
				sb = sb != null ? sb : new StringBuffer();
				if (isPrevExcluded(text, matcher) || isNextExcluded(text, matcher) || isWithinAnchor(text, matcher)) {
					matcher.appendReplacement(sb, key);
				}
				else {
					matcher.appendReplacement(sb, "[" + key + "](" + issueBaseUrl + key + ")");
				}
			}
			if (sb != null) {
				matcher.appendTail(sb);
				text.setChars(CharSubSequence.of(sb));
			}
		}

		private static boolean isPrevExcluded(Text text, Matcher matcher) {
			int start = matcher.start();
			Character prev = start > 0 ? text.getChars().charAt(start - 1) : null;
			return prev != null && excludedCharsBeforeJiraIssueKey.contains(prev);
		}

		private static boolean isNextExcluded(Text text, Matcher matcher) {
			int end = matcher.end();
			Character next = end < text.getChars().length() ? text.getChars().charAt(end) : null;
			return next != null && next == '-';
		}

		private static boolean isWithinAnchor(Text text, Matcher matcher) {
			return matcher.start() == 0 && text.getPrevious() instanceof HtmlInline &&
					text.getPrevious().getChars().startsWith("<a");
		}
	}

	/**
	 * Visit Markdown reference-style links and:
	 * 1) Remove surrounding square brackets if the content between them is a URL.
	 * Such links were not meant to be Markdown reference-style link.
	 * 2) Replace Jira issue keys surrounded with square brackets with a link to the issue.
	 */
	private static class ReferenceStyleLinkVisitor implements Visitor<LinkRef> {

		private final Pattern jiraKeyPattern;

		private final String issueBaseUrl;

		ReferenceStyleLinkVisitor(Pattern jiraKeyPattern, String issueBaseUrl) {
			this.jiraKeyPattern = jiraKeyPattern;
			this.issueBaseUrl = issueBaseUrl;
		}

		@Override
		public void visit(LinkRef linkRef) {
			BasedSequence sequence = linkRef.getChars();
			if (sequence.startsWith("[http://") || sequence.startsWith("[https://")) {
				sequence = sequence.subSequence(1, sequence.length() - 1);
				linkRef.setChars(sequence);
			}
			else {
				BasedSequence content = sequence.subSequence(1, sequence.length() - 1);
				if (jiraKeyPattern.matcher(content).matches()) {
					String issueLink = "[" + content + "](" + issueBaseUrl + content + ")";
					linkRef.setChars(CharSubSequence.of(issueLink));
				}
			}
		}
	}

	/**
	 * Remove square brackets around Markdown inline links, if we end up with any
	 * after the conversion.
	 */
	private static class InlineLinkVisitor implements Visitor<Link> {

		@Override
		public void visit(Link link) {
			Node prev = link.getPrevious();
			Node next = link.getNext();
			if (prev instanceof Text && next instanceof Text) {
				BasedSequence prevSeq = prev.getChars();
				BasedSequence nextSeq = next.getChars();
				if (prevSeq.endsWith("[") && nextSeq.startsWith("]")) {
					prev.setChars(prevSeq.subSequence(0, prevSeq.length() - 1));
					next.setChars(nextSeq.subSequence(1, nextSeq.length()));
				}
			}
		}
	}

}