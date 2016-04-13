/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.actuate.endpoint.mvc;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JvmDiagnosticsMvcEndpointTests {

	@Test
	public void test_that_histogram_is_parsed() {
		String header = " num     #instances         #bytes  class name\n----------------------------------------------\n";
		String row1 = "   1:        194776       25070408  [C\n";
		String row2 = "   2:        167948        5374336  java.util.HashMap$Node\n";
		String row3 = "   3:        194459        4667016  java.lang.String\n";
		String footer = "Total        557183       35111760\n";
		String jmapHistogram = header + row1 + row2 + row3 + footer;

		JvmDiagnosticsMvcEndpoint.JmapHistogramParser histogramParser = new JvmDiagnosticsMvcEndpoint.JmapHistogramParser();
		Map<String, Object> parsed = histogramParser.parse(jmapHistogram);
		assertThat(parsed.get("totalInstances")).isEqualTo(557183);
		assertThat(parsed.get("totalBytes")).isEqualTo(35111760);

		List<Map<String, Object>> records = (List<Map<String, Object>>) parsed.get("records");
		assertThat(records.size()).isEqualTo(3);
		assertThat(records.get(0).get("instances")).isEqualTo(194776);
		assertThat(records.get(0).get("bytes")).isEqualTo(25070408);
		assertThat(records.get(0).get("class")).isEqualTo("[C");
		assertThat(records.get(1).get("instances")).isEqualTo(167948);
		assertThat(records.get(1).get("bytes")).isEqualTo(5374336);
		assertThat(records.get(1).get("class")).isEqualTo("java.util.HashMap$Node");
		assertThat(records.get(2).get("instances")).isEqualTo(194459);
		assertThat(records.get(2).get("bytes")).isEqualTo(4667016);
		assertThat(records.get(2).get("class")).isEqualTo("java.lang.String");
	}
}
