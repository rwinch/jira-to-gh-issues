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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lombok.AllArgsConstructor;

/**
 * @author Rob Winch
 *
 */
public class JiraFixVersionTests {

	@Test
	public void sortMajorVersion() {
		assertFixVersions("1.0.0","2.0.0").sortsAs("2.0.0","1.0.0");
		assertFixVersions("1.0","2.0").sortsAs("2.0","1.0");
		assertFixVersions("1.1.1","2.0").sortsAs("2.0","1.1.1");
	}

	@Test
	public void sortMinorVersion() {
		assertFixVersions("0.9.0","0.10.0").sortsAs("0.10.0","0.9.0");
	}

	@Test
	public void sortPatchVersion() {
		assertFixVersions("0.9.0","0.9.1").sortsAs("0.9.1","0.9.0");
		assertFixVersions("4.0","4.0.1").sortsAs("4.0.1","4.0");
		assertFixVersions("4.0","4.1.1").sortsAs("4.1.1","4.0");
	}

	@Test
	public void sortMilestoneVersion() {
		assertFixVersions("4.0 M1","4.0").sortsAs("4.0","4.0 M1");
		assertFixVersions("4.0 M1","4.0.1").sortsAs("4.0.1","4.0 M1");
		assertFixVersions("4.0 M1","4.0 M2").sortsAs("4.0 M2","4.0 M1");
		assertFixVersions("4.0 M1","4.0 RC1").sortsAs("4.0 RC1","4.0 M1");
		assertFixVersions("4.0 RC1","4.0 RC2").sortsAs("4.0 RC2","4.0 RC1");
		assertFixVersions("5.1 RC3","5.1 GA").sortsAs("5.1 GA","5.1 RC3");
	}

	@Test
	public void sortAllSecurityVersions() {
		assertFixVersions("3.0.0.RC2", "1.0.6", "0.8.2", "3.0.4", "3.1.0.RC3", "3.2.0", "2.0.8", "3.1.2", "4.0.2", "3.0.7", "3.2.8", "3.0.0 RC1", "3.0.9", "3.0.0 M1", "2.0.1", "3.0.8", "3.0.5", "1.0.4", "3.2.0.RC1", "1.0.0", "3.2.0.RC2", "1.0.0 RC1", "3.2.0.M1", "3.0.0", "3.2.9", "2.0.0 RC1", "3.1.0.M2", "2.0.3", "3.0.0 M2", "3.2.7", "3.2.3", "3.2.5", "3.1.5", "3.1.6", "3.1.3", "3.1.7", "3.1.4", "3.2.10", "3.1.0.RC2", "2.0.0 M2", "4.0.0.RC1", "3.0.1", "4.1.0 M1", "3.1.0.M1", "4.0.0.M2", "3.1.0", "1.0.7", "0.9.0", "3.0.3", "2.0.6", "3.1.0.RC1", "1.0.1", "2.0.9", "1.0.5", "2.0.2", "4.0.0", "2.0.5", "2.0.4", "2.0.7", "3.2.1", "0.8.3", "3.2.4", "3.1.1", "4.0.0.M1", "3.2.6", "3.2.2", "1.0.2", "4.0.1", "1.0.3", "3.0.6", "4.0.4", "4.0.3", "3.0.2", "4.0.0.RC2", "2.0.0 M1", "3.2.0.M2", "3.1.8", "1.0.0 RC2", "2.0.0")
			.sortsAs("4.1.0 M1", "4.0.4", "4.0.3", "4.0.2", "4.0.1", "4.0.0", "4.0.0.RC2", "4.0.0.RC1", "4.0.0.M2", "4.0.0.M1", "3.2.10", "3.2.9", "3.2.8", "3.2.7", "3.2.6", "3.2.5", "3.2.4", "3.2.3", "3.2.2", "3.2.1", "3.2.0", "3.2.0.RC2", "3.2.0.RC1", "3.2.0.M2", "3.2.0.M1", "3.1.8", "3.1.7", "3.1.6", "3.1.5", "3.1.4", "3.1.3", "3.1.2", "3.1.1", "3.1.0", "3.1.0.RC3", "3.1.0.RC2", "3.1.0.RC1", "3.1.0.M2","3.1.0.M1","3.0.9", "3.0.8", "3.0.7", "3.0.6", "3.0.5", "3.0.4",  "3.0.3", "3.0.2", "3.0.1", "3.0.0", "3.0.0.RC2", "3.0.0 RC1", "3.0.0 M2", "3.0.0 M1", "2.0.9", "2.0.8", "2.0.7",     "2.0.6",   "2.0.5", "2.0.4", "2.0.3", "2.0.2", "2.0.1", "2.0.0", "2.0.0 RC1", "2.0.0 M2", "2.0.0 M1", "1.0.7", "1.0.6", "1.0.5", "1.0.4", "1.0.3", "1.0.2", "1.0.1", "1.0.0", "1.0.0 RC2", "1.0.0 RC1", "0.9.0", "0.8.3", "0.8.2");
	}

	private JiraFixVersionAssertion assertFixVersions(String ...names) {
		return new JiraFixVersionAssertion(names);
	}

	@AllArgsConstructor
	static class JiraFixVersionAssertion {
		private String[] names;

		public void sortsAs(String... names) {
			List<JiraFixVersion> toSort = create(this.names);
			toSort.sort(JiraFixVersion.comparator());
			assertThat(toSort).extracting(JiraFixVersion::getName).containsExactly(names);
		}

		private static List<JiraFixVersion> create(String...names) {
			List<JiraFixVersion> versions = new ArrayList<>();
			for(String name : names) {
				versions.add(new JiraFixVersion(name));
			}
			return versions;
		}
	}
}