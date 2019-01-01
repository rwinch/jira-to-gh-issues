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
import java.util.stream.Collectors;

import com.vladsch.flexmark.ast.Document;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.HtmlBlock;
import com.vladsch.flexmark.ast.HtmlCommentBlock;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.HtmlInlineComment;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.VisitHandler;
import com.vladsch.flexmark.ast.Visitor;
import com.vladsch.flexmark.formatter.internal.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.block.NodePostProcessor;
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory;
import com.vladsch.flexmark.util.NodeTracker;
import com.vladsch.flexmark.util.sequence.BasedSequence;
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

	private static final Pattern jiraUserMentionPattern = Pattern.compile("\\[~([^]]+)]");

	private static final Pattern tablesPattern = Pattern.compile("(?m)^[ \\t]*(\\|\\|.*\\|\\|)[ \\t]*$");


	String jiraBaseUrl;

	private Map<String, JiraUser> userLookup = new HashMap<>();

	/** Escape/suppress emphasis over multiple lines, ahead of other transformations. */
	private Parser phase1Parser;
	/** All other transformations. */
	private Parser phase2Parser;
	/** Render transformed markdown */
	private final Formatter formatter = Formatter.builder().build();
	/** For things that can't be done as easily with a post processor */
	private final NodeVisitor visitor = new NodeVisitor(new VisitHandler<>(Link.class, new InlineLinkVisitor()));


	@Autowired
	public void setJiraConfig(JiraConfig jiraConfig) {
		this.jiraBaseUrl = jiraConfig.getBaseUrl();
		phase1Parser = Parser.builder().postProcessorFactory(new Phase1NodePostProcessorFactory()).build();
		phase2Parser = Parser.builder().postProcessorFactory(new Phase2NodePostProcessorFactory(jiraConfig)).build();
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

		Assert.notNull(this.phase1Parser, "phase1Parser not initialized.");
		Assert.notNull(this.phase2Parser, "phase2Parser not initialized.");

		if (!StringUtils.hasLength(text)) {
			return "";
		}

		// Lists and headings (in that order!)
		text = lists(text);
		text = headings(text);
		text = tables(text);
		text = text.replaceAll("(?m)^[ ]{0,3}---[ \\t]*$", "&mdash;");

		// Code
		text = text.replaceAll("\\{\\{(.+?)\\}\\}", "`$1`"); // inline code
		text = text.replaceAll("\\{(code|noformat|panel)(:(\\w+))?(?:(:|\\|)\\w+=.+?)*\\}", "```$3 ");
		text = text.replaceAll("(```\\w*) (.+)", "$1\n$2");
		text = text.replaceAll("(.)(```) ", "$1\n$2");

		// Quotes
		text = quoteBlocks(text);
		text = text.replaceAll("(?m)^[ \\t]*bq\\.", "> "); // single line quotes
		text = text.replaceAll("\\{(color)(:((#)?\\w+))?(?:(:|\\|)\\w+=.+?)*\\}", ""); // color tags

		text = text.replaceAll("\\[(.+?)\\|(http.*?)\\]", "[$1]($2)"); // links
		text = replaceUserKeyWithDisplayNameInJiraUserMentions(text);

		do {
			Node node = phase1Parser.parse(text);
			text = formatter.render(node);
		}
		while (phase1PostProcessor.keepProcessing());

		Node node = phase2Parser.parse(text);
		visitor.visit(node);
		text = formatter.render(node);

		return text;
	}

	private String lists(String text) {

		// Tasks in lists (lists handled separately below)
		text = text.replaceAll("(?m)^[ \\t]*(([#]{1,2}+|[-]{1,2}+)([-]{0,2}|[*]{0,2}) )(\\(x\\))", "$1[ ]");
		text = text.replaceAll("(?m)^[ \\t]*(([#]{1,2}+|[-]{1,2}+)([-]{0,2}|[*]{0,2}) )(\\(/\\))", "$1[x]");
		text = text.replaceAll("(?m)^[ \\t]*(([#]{1,2}+|[-]{1,2}+)([-]{0,2}|[*]{0,2}) )(\\(!\\))", "$1[ ]");

		// Ordered lists
		// Replacing "#" and "##" disrupts a small number of code snippets, but the impact is small,
		// Empirically in SPR most occurrences of "#" in the beginning of a line are numbered lists.
		// If left alone, unless those are in code blocks, they would be interpreted as headings in
		// markdown and would look even worse. It's also why we can't do this in a Flexmark Text node
		// processor (they're seen as headings by the Flexmark parser).
		text = text.replaceAll("(?m)^[ \\t]*# ",       "1. ");
		text = text.replaceAll("(?m)^[ \\t]*## ",      "   1. ");
		text = text.replaceAll("(?m)^[ \\t]*#- ",      "   - ");
		text = text.replaceAll("(?m)^[ \\t]*#\\* ",    "   * ");
		text = text.replaceAll("(?m)^[ \\t]*### ",     "      1. ");
		text = text.replaceAll("(?m)^[ \\t]*#-- ",     "      - ");
		text = text.replaceAll("(?m)^[ \\t]*#\\*\\* ", "      * ");

		// Unordered lists:
		// No need to convert "-" or "* " (same in Markdown)..
		// For nested lists (e.g. "** ", "-- ", etc) we use a Flexmark Text node processor later on,
		// in order to avoid disrupting occurrences in code blocks.

		return text;
	}

	private String headings(String text) {
		text = text.replaceAll("(?m)^h1. ", "# ");
		text = text.replaceAll("(?m)^h2. ", "## ");
		text = text.replaceAll("(?m)^h3. ", "### ");
		text = text.replaceAll("(?m)^h4. ", "#### ");
		text = text.replaceAll("(?m)^h5. ", "##### ");
		text = text.replaceAll("(?m)^h6. ", "###### ");
		return text;
	}

	private String tables(String text) {
		Matcher matcher = tablesPattern.matcher(text);
		StringBuffer sb = null;
		while (matcher.find()) {
			String[] headings = matcher.group(1).split("\\|\\|");
			headings = Arrays.copyOfRange(headings, 1, headings.length);
			String row1 = Arrays.stream(headings).map(String::trim).collect(Collectors.joining("|", "|", "|\n"));
			String row2 = Arrays.stream(headings).map(s -> ":---").collect(Collectors.joining("|", "|", "|"));
			sb = sb != null ? sb : new StringBuffer();
			matcher.appendReplacement(sb, row1 + row2);
		}
		if (sb == null) {
			return text;
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String quoteBlocks(String str) {
		String[] parts = str.split("\\{quote\\}");
		for (int i = 1; i < parts.length; i += 2) {
			parts[i] = "\n > " + parts[i].replaceAll("\n", "\n> ");
		}
		return StringUtils.arrayToDelimitedString(parts, "");
	}

	private String replaceUserKeyWithDisplayNameInJiraUserMentions(String text) {
		Matcher matcher = jiraUserMentionPattern.matcher(text);
		StringBuffer sb = null;
		while (matcher.find()) {
			String key = matcher.group(1);
			JiraUser user = this.userLookup.computeIfAbsent(key, k -> {
				JiraUser u = new JiraUser();
				u.setKey(k);
				u.setDisplayName(k);
				u.setSelf(this.jiraBaseUrl);
				return u;
			});
			sb = sb != null ? sb : new StringBuffer();
			matcher.appendReplacement(sb, "[" + user.getDisplayName() + "](" + user.getBrowserUrl() + ")");
		}
		if (sb == null) {
			return text;
		}
		matcher.appendTail(sb);
		return sb.toString();
	}


	private static final Phase1NodePostProcessor phase1PostProcessor = new Phase1NodePostProcessor();

	private static class Phase1NodePostProcessorFactory extends NodePostProcessorFactory {

		Phase1NodePostProcessorFactory() {
			super(false);
			addNodes(Emphasis.class);
			addNodes(Text.class);
		}

		@Override
		public NodePostProcessor create(Document document) {
			phase1PostProcessor.reset();
			return phase1PostProcessor;
		}
	}


	private static class Phase1NodePostProcessor extends NodePostProcessor {

		private int modificationCount = 0;


		boolean keepProcessing() {
			return modificationCount > 0;
		}

		void reset() {
			modificationCount = 0;
		}

		@Override
		public void process(NodeTracker state, Node node) {
			String content = node.getChars().toString();
			if (node instanceof Emphasis) {
				// "*/*" - media type related, not properly escaped, not rendering correctly even in Jira
				if (content.equals("*/*")) {
					replaceNodeWithText(state, node, "\\*/\\*");
				}
				// "*."  - majority of these are package statements or aspectj pointcuts
				else if (content.equals("*.*")) {
					replaceNodeWithText(state, node, "\\*.\\*");
				}
				// "\n"  - emphasis over multiple lines is legal but in 99% of cases not actually an emphasis
				else if (content.contains("\n")) {
					modificationCount++;
					content = "\\" + content;
					replaceNodeWithText(state, node, content);
				}
			}
		}

		private void replaceNodeWithText(NodeTracker state, Node node, String content) {
			Text text = new Text(content);
			node.insertAfter(text);
			state.nodeAdded(text);
			node.unlink();
			state.nodeRemoved(node);
		}
	}


	private static class Phase2NodePostProcessorFactory extends NodePostProcessorFactory {

		private final JiraConfig jiraConfig;


		Phase2NodePostProcessorFactory(JiraConfig jiraConfig) {
			super(false);
			this.jiraConfig = jiraConfig;
			addNodes(Emphasis.class);
			addNodes(HtmlBlock.class, HtmlCommentBlock.class, HtmlInline.class, HtmlInlineComment.class);
			addNodes(LinkRef.class);
			addNodes(Text.class);
		}

		@Override
		public NodePostProcessor create(Document document) {
			return new Phase2NodePostProcessor(jiraConfig);
		}
	}


	private static class Phase2NodePostProcessor extends NodePostProcessor {

		private static final Pattern ghUserMentionPattern = Pattern.compile("(^|[^\\w])(@[\\w-]+)");

		private static final Pattern jiraKeyPattern = Pattern.compile("(" +
				"(GREENHOUSE|IMPALA|GRADLE|SHDPADMIN|AMQP|AMQPNET|BATCH|" +
				"BATCHADM|FLEX|DATAAERO|DATACOL|DATACMNS|DATACOUCH|DATAES|DATACASS|DATASOLR|SGF|DATAGEODE|DATAHB|" +
				"DATAJDBC|DATAJPA|DATAKV|DATALDAP|DATAMAP|DATAMONGO|DATAGRAPH|DATAREDIS|DATAREST|DATARIAK|ANDROID|" +
				"SHDP|SPR|SGFNET|IDE|INT|INTEXT|INTDSLGROOVY|INTROO|INTSAMPLES|INTSCALA|INTTEMPLATES|SJC|LDAP|SMA|" +
				"MOBILE|MOD|OSGI|SPS|RCP|ROO|ROOFLEX|SCALA|SEC|SECOAUTH|SHL|SLICE|SOCIAL|SOCIALFB|SOCIALGH|" +
				"SOCIALLI|SOCIALTI|SOCIALTW|STS|SWF|SWS|XD|SPRNET|SPRNETCODECONFIG|SPRNETREST|SPRNETSOCIAL|" +
				"SPRNETSOCIALDB|SPRNETSOCIALFB|SPRNETSOCIALLI|SPRNETSOCIALTW|SPRNETVSADDIN|SESPRINGACTIONSCRIPTAS|" +
				"SEBLOB|SECOUCHDB|SEDBFONET|SEDBFO|SE|SEBATCHNET|SECONFIGNET|SENMSNET|SERICHCLIENTNET|SETHREADNET|" +
				"SESIA|SESPRINGINTEGRATIONNET|SEJCR|SESPRINGPYTHONPY|SES|SESQLJ|SESURF|SEWORKFLOW|SEWPFNET)"+
				"-[0-9]{1,5}+)");

		/**
		 * Derived by using {@link io.pivotal.jira.report.MarkupConverter} to dump
		 * descriptions and comments for all issues and grepping for examples.
		 */
		private static final List<Character> skipCharsPrecedingJiraIssueKey =
				Arrays.asList('/', '-', ':', '^', '@', '\\', '=', '"', '\'', '`');


		private final String jiraIssueBaseUrl;


		Phase2NodePostProcessor(JiraConfig jiraConfig) {
			this.jiraIssueBaseUrl = jiraConfig.getBaseUrl() + "/browse/";
		}


		@Override
		public void process(NodeTracker state, Node node) {
			String content = node.getChars().toString();
			if (node instanceof Text) {
				content = applyGhUserMentionPattern(content);
				content = replaceJiraKeysWithLinks(node, content);
				// Unordered lists:
				// no need to convert "-" or "* " (same in Markdown).
				content = content.replaceAll("(?m)^[ \\t]*\\*\\* ", "   * ");
				content = content.replaceAll("(?m)^[ \\t]*-- ",     "   - ");
				content = content.replaceAll("(?m)^[ \\t]*-# ",     "   1. ");
				content = content.replaceAll("(?m)^[ \\t]*\\*# ",   "   1. ");
			}
			else if (node instanceof LinkRef) {
				if (content.startsWith("[http://") || content.startsWith("[https://")) {
					// For Jira these are a variation on a link that doesn't have an alias
					// but in markdown they're reference-style links and are rendered with
					// the square brackets, so remove the brackets.
					content = content.substring(1, content.length() - 1);
				}
				else {
					String s = content.substring(1, content.length() - 1);
					if (jiraKeyPattern.matcher(s).matches()) {
						content = getJiraIssueUrl(s);
					}
				}
			}
			else if (node instanceof Emphasis){
				if (content.charAt(0) == '*' && content.charAt(content.length()-1) == '*') {
					// Jira bold "*" to markdown bold "**"
					content = content.substring(1, content.length() - 1);
					content = applyGhUserMentionPattern(content);
					content = "**" + content + "**";
				}
				// else: emphasis with "_"
			}
			else if (node instanceof HtmlBlock || node instanceof HtmlCommentBlock ||
					node instanceof HtmlInline || node instanceof HtmlInlineComment) {

				if (content.startsWith("<a ") || content.equals("</a>")) {
					return;
				}
				content = content.replaceAll("<", "\\\\<");
			}
			replaceNodeWithText(state, node, content);
		}

		private String applyGhUserMentionPattern(String text) {
			// Escape "@"-prefixed content to avoid incidental GitHub user mentions.
			return ghUserMentionPattern.matcher(text).replaceAll("$1`$2`");
		}

		private String replaceJiraKeysWithLinks(Node node, String text) {
			Matcher matcher = jiraKeyPattern.matcher(text);
			StringBuffer sb = null;
			while (matcher.find()) {
				String key = matcher.group(1);
				sb = sb != null ? sb : new StringBuffer();
				if (skipJiraKeyMatch(matcher, text, node)) {
					matcher.appendReplacement(sb, key);
				}
				else {
					matcher.appendReplacement(sb, getJiraIssueUrl(key));
				}
			}
			if (sb != null) {
				matcher.appendTail(sb);
				return sb.toString();
			}
			return text;
		}

		private static boolean skipJiraKeyMatch(Matcher matcher, String text, Node node) {
			int start = matcher.start();
			int end = matcher.end();
			Character prevChar = start > 0 ? text.charAt(start - 1) : null;
			Character nextChar = end < text.length() ? text.charAt(end) : null;
			Node prevNode = node.getPrevious();
			return prevChar != null && skipCharsPrecedingJiraIssueKey.contains(prevChar) ||
					nextChar != null && nextChar == '-' ||
					matcher.start() == 0 && prevNode instanceof HtmlInline && prevNode.getChars().startsWith("<a");
		}

		private String getJiraIssueUrl(String content) {
			return "[" + content + "](" + jiraIssueBaseUrl + content + ")";
		}

		private void replaceNodeWithText(NodeTracker state, Node node, String content) {
			Text text = new Text(content);
			node.insertAfter(text);
			state.nodeAdded(text);
			node.unlink();
			state.nodeRemoved(node);
		}
	}


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