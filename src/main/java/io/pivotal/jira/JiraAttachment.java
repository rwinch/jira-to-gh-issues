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
package io.pivotal.jira;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class JiraAttachment {
	String filename;
	String content;
	int size;


	private static final BigDecimal KB_DIVISOR = new BigDecimal(1024);

	private static final BigDecimal MB_DIVISOR = KB_DIVISOR.multiply(KB_DIVISOR);


	public String getSizeToDisplay() {
		if (size > MB_DIVISOR.intValue()) {
			return new BigDecimal(size).divide(MB_DIVISOR, 2, BigDecimal.ROUND_UP) + " MB";
		}
		else if (size > KB_DIVISOR.intValue()) {
			return new BigDecimal(size).divide(KB_DIVISOR, 2, BigDecimal.ROUND_UP) + " kB";
		}
		else {
			return size + " bytes";
		}
	}
}
