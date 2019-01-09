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

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ast.Document;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.formatter.internal.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.block.NodePostProcessor;
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory;
import com.vladsch.flexmark.util.NodeTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Rossen Stoyanchev
 */
public class JiraLinkConverter {

	private static final Logger logger = LogManager.getLogger(JiraLinkConverter.class);


	private final Pattern rawKiraLinkPattern;

	private final Map<String, Integer> issueMappings;

	private final Writer failWriter;

	private final Parser parser;

	private final Formatter formatter = Formatter.builder().build();


	public JiraLinkConverter(String jiraProject, Map<String, Integer> issueMappings, Writer failWriter) {

		this.rawKiraLinkPattern = Pattern.compile(
				"(https://jira\\.spring\\.io/browse/(" + jiraProject + "-[0-9]{1,5}+)([^?]))");

		this.issueMappings = issueMappings;
		this.failWriter = failWriter;
		JiraLinkPostProcessorFactory factory = new JiraLinkPostProcessorFactory(jiraProject, issueMappings);
		this.parser = Parser.builder().postProcessorFactory(factory).build();
	}


	public String convert(String body) {

		Node node = parser.parse(body);
		body = formatter.render(node);

		try {
			body = replaceRawJiraLinks(body, issueMappings);
		}
		catch (Throwable ex) {
			try {
				failWriter.write("Failed to replace raw link: " + ex.getMessage() +
						"\n|||||||||||||||||||||\n" + body + "\n|||||||||||||||||||||\n");
			}
			catch (IOException ex2) {
				logger.error("Failed to write error: " + ex2.getMessage());
			}
		}

		return body;
	}

	private String replaceRawJiraLinks(String body, Map<String, Integer> issueMappings) throws IOException {
		Matcher matcher = rawKiraLinkPattern.matcher(body);
		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String jiraKey = matcher.group(2);
			Integer ghIssueId = issueMappings.get(jiraKey);
			if (ghIssueId == null) {
				failWriter.write("No mapping for " + jiraKey + "\n");
				failWriter.flush();
				matcher.appendReplacement(sb, matcher.group(1));
			}
			else {
				matcher.appendReplacement(sb, "#" + ghIssueId + (matcher.group(3) != null ? matcher.group(3) : ""));
			}
		}
		matcher.appendTail(sb);
		return sb.toString();
	}



	private static class JiraLinkPostProcessorFactory extends NodePostProcessorFactory {

		private JiraLinkPostProcessor processor;


		JiraLinkPostProcessorFactory(String projectId, Map<String, Integer> issueMappings) {
			super(false);
			processor = new JiraLinkPostProcessor(projectId, issueMappings);
			addNodes(Link.class);
		}


		@Override
		public NodePostProcessor create(Document document) {
			return processor;
		}
	}


	private static class JiraLinkPostProcessor extends NodePostProcessor {

		private final Pattern sprKeyPattern;

		private final Map<String, Integer> issueMappings;


		JiraLinkPostProcessor(String projectId, Map<String, Integer> issueMappings) {
			this.sprKeyPattern = Pattern.compile("(" + projectId + "-[0-9]{1,5}+)");
			this.issueMappings = issueMappings;
		}

		@Override
		public void process(NodeTracker state, Node node) {
			if (node instanceof Link) {
				String targetText = ((Link) node).getText().toString();
				String targetLink = ((Link) node).getUrl().toString();
				if (sprKeyPattern.matcher(targetText).matches() && targetLink.endsWith(targetText)) {
					Integer ghIssueId = issueMappings.get(targetText);
					if (ghIssueId != null) {
						Text textNode = new Text("#" + ghIssueId);
						node.insertAfter(textNode);
						state.nodeAdded(textNode);
						node.unlink();
						state.nodeRemoved(node);
					}
				}
			}
		}
	}
}
