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
package io.pivotal.jira;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Rob Winch
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class JiraFixVersion {
	String name;

	public static List<JiraFixVersion> sort(List<JiraFixVersion> versions) {
		List<JiraFixVersion> toSort = new ArrayList<>(versions);
		Collections.sort(toSort, new JiraFixVersionComparator());
		return toSort;
	}

	private static class JiraFixVersionComparator implements Comparator<JiraFixVersion> {
		static final String PARTS_EXPRESSION = "[\\. ]";

		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		@Override
		public int compare(JiraFixVersion lhs, JiraFixVersion rhs) {
			String[] lhsParts = lhs.getName().split(PARTS_EXPRESSION);
			String[] rhsParts = rhs.getName().split(PARTS_EXPRESSION);

			for(int i=0;i<Math.max(lhsParts.length, rhsParts.length);i++) {
				int part = comparePart(i, lhsParts, rhsParts);
				if(part != 0) {
					return -1 * part;
				}
			}
			return 0;
		}

		private int comparePart(int index, String[] lhs, String[] rhs) {
			return getPart(lhs,index) - getPart(rhs,index);
		}

		private int getPart(String[] parts,int index) {
			if(index >= parts.length) {
				return 0;
			}
			String toParse = parts[index];
			try {
				return Integer.parseInt(toParse);
			} catch(NumberFormatException e) {
				return toNumber(toParse);
			}
		}

		private int toNumber(String str) {
			int n = Integer.MIN_VALUE;
			for(int i=0;i<str.length();i++) {
				n += str.charAt(i);
			}
			return n;
		}
	}
}
