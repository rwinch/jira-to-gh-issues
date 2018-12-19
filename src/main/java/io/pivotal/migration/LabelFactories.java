/*
 * Copyright 2002-2018 the original author or authors.
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
package io.pivotal.migration;

import java.util.function.Function;

import org.eclipse.egit.github.core.Label;


/**
 * @author Rossen Stoyanchev
 */
public class LabelFactories {

	public static final Function<String, Label> TYPE_LABEL = name -> create("type: ", name, "000000");

	public static final Function<String, Label> STATUS_LABEL = name -> create("status: ", name, "f7e983");

	public static final Function<String, Label> IN_LABEL = name -> create("in: ", name, "008672");

	public static final Function<String, Label> HAS_LABEL = name -> create("has: ", name, "b8daf2");


	private static Label create(String prefix, String labelName, String color) {
		Label label = new Label();
		label.setName(prefix + labelName);
		label.setColor(color);
		return label;
	}

}
