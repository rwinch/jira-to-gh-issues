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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Document;
import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.formatter.internal.Formatter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.block.NodePostProcessor;
import com.vladsch.flexmark.parser.block.NodePostProcessorFactory;
import com.vladsch.flexmark.util.Function;
import com.vladsch.flexmark.util.NodeTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.util.StringUtils;

/**
 * The issues addressed by this converter were discovered only after the migration.
 * They can however be done as part of the migration in future migrations, so ideally
 * look to integrate them into {@link io.pivotal.util.MarkdownEngine}.
 * <p>The converter does the below:
 * <ul>
 * <li>Replace remaining annotations in text (colliding with GH user mentions).
 * Those that were in emphasis were not properly escaped on the first pass,
 * <li>Replace occurrences of lines consisting of "-" or "=" only with 3 dashes
 * preceded by an empty line. See {@link #cleanupHorizontalLines(String)}.
 * <li>Correct issues stemming from the escaping of "{" and "}".
 * </ul>
 *
 * @author Rossen Stoyanchev
 */
public class PostMigrationConverter {

	private static final Logger logger = LogManager.getLogger(PostMigrationConverter.class);


	private final Writer failWriter;

	private final Parser parser;

	private final Formatter formatter = Formatter.builder().build();

	private final Pattern horizontalLinesPattern = Pattern.compile("^([-]+|[=]+)$");


	public PostMigrationConverter(Writer failWriter) {
		this.failWriter = failWriter;
		this.parser = Parser.builder().postProcessorFactory(new PostMigrationProcessorFactory()).build();
	}


	public String convert(String body, AtomicBoolean failed) {
		try {
			body = cleanupHorizontalLines(body);

			// Note the below are in part the results of the conversion of MarkdownEngine not anticipating escaped "{" and "}"
			// Ideally those should have been handled correctly by MarkdwonEngine from the start
			body = convertNonCodeSections(body, text -> {
				text = text.replaceAll("\\\\\\{", "{");	// drop escaping of "{"
				text = text.replaceAll("\\\\}", "}");   // drop escaping of "}"
				text = text.replaceAll("\\\\`}", "}`"); // correct situations with triple "\{{{foo}}\}"
				text = text.replaceAll("\\\\`", "`"); // drop "\`" which originally would have been "\{{"
				return text;
			});
		}
		catch (Throwable ex) {
			failed.set(true);
			try {
				failWriter.write("Failed to process horizontal lines: " + ex.getMessage() +
						"\n|||||||||||||||||||||\n" + body + "\n|||||||||||||||||||||\n");
			}
			catch (IOException ex2) {
				logger.error("Failed to write error: " + ex2.getMessage());
			}
		}
		try {
			Node node = parser.parse(body);
			return formatter.render(node);
		}
		catch (Throwable ex) {
			failed.set(true);
			try {
				failWriter.write("Markdown parser failure: " + ex.getMessage() +
						"\n|||||||||||||||||||||\n" + body + "\n|||||||||||||||||||||\n");
			}
			catch (IOException e1) {
				logger.error("Failed to write error: " + e1.getMessage());
			}
		}
		return body;
	}

	private String convertNonCodeSections(String body, Function<String, String> textConverter) {
		if (!StringUtils.hasLength(body)) {
			return body;
		}
		StringBuilder sb = new StringBuilder();
		StringBuilder text = new StringBuilder();
		boolean inCodeBlock = false;
		StringTokenizer tokenizer = new StringTokenizer(body, "\n", true);
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (token.startsWith("```")) {
				if (!inCodeBlock) {
					sb.append(textConverter.apply(text.toString()));
					text = new StringBuilder();
				}
				inCodeBlock = !inCodeBlock;
				sb.append(token);
			}
			else {
				(inCodeBlock ? sb : text).append(token);
			}
		}
		if (text.length() > 0) {
			sb.append(textConverter.apply(text.toString()));
		}
		return sb.toString();
	}


	/**
	 * Convert all occurrences of lines consisting of "-" or only "=" only.
	 * <p>The dashes are considered a horizontal line in both Jira and markdown
	 * but without an empty line preceding it, it becomes a heading in markdown
	 * causing very ugly results especially after stack traces.
	 * <p>The equals signs has no meaning in Jira markup and appears as is,
	 * typically used as an alternative horizontal line. However in markdown the
	 * preceding line becomes a heading if not empty.
	 * <p>Both are replaced with 3 dashes "---" also ensuring an empty line
	 * before that, so the result is a horizontal line.
	 */
	private String cleanupHorizontalLines(String body) throws IOException {
		BufferedReader bufferedReader = new BufferedReader(new StringReader(body));
		StringBuilder sb = new StringBuilder();
		String prevLine = null;
		String currLine;
		boolean inCodeBlock = false;
		while ((currLine = bufferedReader.readLine()) != null) {
			boolean prevLineNotEmpty = prevLine != null && !prevLine.trim().isEmpty();
			if (!inCodeBlock && horizontalLinesPattern.matcher(currLine).matches()) {
				if (prevLineNotEmpty) {
					sb.append("\n");
				}
				sb.append("---\n");
			}
			else if (currLine.startsWith("```")) {
				inCodeBlock = !inCodeBlock;
				sb.append(currLine).append("\n");
			}
			else {
				sb.append(currLine).append("\n");
			}
			prevLine = currLine;
		}
		return sb.toString();
	}

	private static class PostMigrationProcessorFactory extends NodePostProcessorFactory {

		private PostMigrationProcessor processor;

		PostMigrationProcessorFactory() {
			super(false);
			processor = new PostMigrationProcessor();
			addNodes(Text.class);
			addNodes(Code.class);
		}

		@Override
		public NodePostProcessor create(Document document) {
			return processor;
		}
	}


	private static class PostMigrationProcessor extends NodePostProcessor {

		private static final Pattern ghUserMentionPattern = Pattern.compile("(^|[^\\w])(@[\\w-]+)");


		@Override
		public void process(NodeTracker state, Node node) {
			String textBefore = node.getChars().toString();
			String textAfter = textBefore;
			if (node instanceof Code) {
				textAfter = textBefore.replace("\\<", "<");
			}
			else if (node instanceof Text) {
				textAfter = ghUserMentionPattern.matcher(textBefore).replaceAll("$1`$2`");
			}
			if (!textBefore.equals(textAfter)) {
				replace(state, node, textAfter);
			}
		}

		private void replace(NodeTracker state, Node node, String textAfter) {
			Text textNode = new Text(textAfter);
			node.insertAfter(textNode);
			state.nodeAdded(textNode);
			node.unlink();
			state.nodeRemoved(node);
		}
	}
}
