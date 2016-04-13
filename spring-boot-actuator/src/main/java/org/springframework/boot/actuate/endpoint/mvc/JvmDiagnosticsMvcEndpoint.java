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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.ReflectionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * {@link MvcEndpoint} to expose some jcmd diagnostic commands.
 *
 * @author Lari Hotari
 */
@ConfigurationProperties("endpoints.jvmdiagnostics")
@HypermediaDisabled
public class JvmDiagnosticsMvcEndpoint extends AbstractMvcEndpoint
		implements MvcEndpoint {
	private static final Log logger = LogFactory.getLog(JvmDiagnosticsMvcEndpoint.class);
	DiagnosticsCmdHelper diagnosticsCmdHelper = new DiagnosticsCmdHelper();
	JmapHistogramParser histogramParser = new JmapHistogramParser();
	private final ResponseEntity<Map<String, String>> DIAGNOSTICS_NOT_AVAILABLE_RESPONSE = new ResponseEntity<Map<String, String>>(
			Collections.singletonMap("message",
					"JVM diagnostics is not supported on this JVM. com.sun.management.DiagnosticCommandMBean requires Oracle / OpenJDK 8+."),
			HttpStatus.NOT_FOUND);

	public JvmDiagnosticsMvcEndpoint() {
		setPath("/jvmdiagnostics");
	}

	@RequestMapping(path = "/jmap-histo", method = RequestMethod.GET)
	public ResponseEntity classHistogram() throws MBeanException, ReflectionException {
		return callDiagnosticsMethod("gcClassHistogram");
	}

	@RequestMapping(path = "/jmap-histo.json", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity classHistogramJson()
			throws MBeanException, ReflectionException {
		if (!diagnosticsCmdHelper.isAvailable()) {
			return DIAGNOSTICS_NOT_AVAILABLE_RESPONSE;
		}
		Map<String, Object> results = histogramParser
				.parse(diagnosticsCmdHelper.callDiagnosticsMethod("gcClassHistogram"));
		return new ResponseEntity(results, HttpStatus.OK);
	}

	@RequestMapping(path = "/jstack", method = RequestMethod.GET)
	public ResponseEntity threadDump() throws MBeanException, ReflectionException {
		return callDiagnosticsMethod("threadPrint");
	}

	private ResponseEntity callDiagnosticsMethod(String actionName, String... params)
			throws ReflectionException, MBeanException {
		if (!diagnosticsCmdHelper.isAvailable()) {
			return DIAGNOSTICS_NOT_AVAILABLE_RESPONSE;
		}
		return new ResponseEntity<String>(
				diagnosticsCmdHelper.callDiagnosticsMethod(actionName, params),
				HttpStatus.OK);
	}

	/**
	 *  Uses com.sun.management.DiagnosticCommandMBean to invoke JVM diagnostic commands.
 	 */
	static class DiagnosticsCmdHelper {
		private final static String[] SIGNATURE = { String[].class.getName() };
		DynamicMBean diagnosticCommandMBean;

		DiagnosticsCmdHelper() {
			try {
				Class<?> managementFactoryHelperClass = getClass().getClassLoader()
						.loadClass("sun.management.ManagementFactoryHelper");
				Method getDiagnosticCommandMBeanMethod = ReflectionUtils.findMethod(
						managementFactoryHelperClass, "getDiagnosticCommandMBean");
				diagnosticCommandMBean = (DynamicMBean) ReflectionUtils
						.invokeMethod(getDiagnosticCommandMBeanMethod, null);
			}
			catch (Exception e) {
				logger.warn("Unable to locate com.sun.management.DiagnosticCommandMBean.",
						e);
				diagnosticCommandMBean = null;
			}
		}

		boolean isAvailable() {
			return diagnosticCommandMBean != null;
		}

		String callDiagnosticsMethod(String actionName, String... params)
				throws ReflectionException, MBeanException {
			return (String) diagnosticCommandMBean.invoke(actionName,
					new Object[] { params }, SIGNATURE);
		}
	}

	/**
	 * Parser for jmap -histo output.
	 */
	static class JmapHistogramParser {
		Map<String, Object> parse(String gcClassHistogram) {
			Map<String, Object> root = new LinkedHashMap<String, Object>();
			List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
			root.put("records", records);

			String[] lines = gcClassHistogram.split("\\r?\\n");
			for (int i = 0; i < lines.length; i++) {
				if (i > 1) {
					String line = lines[i];
					String[] parts = line.trim().split("\\s+");
					if (line.startsWith("Total")) {
						if (parts.length >= 3) {
							root.put("totalInstances", Integer.parseInt(parts[1]));
							root.put("totalBytes", Integer.parseInt(parts[2]));
						}
					}
					else if (parts.length == 4) {
						Map<String, Object> record = new LinkedHashMap<String, Object>();
						record.put("class", parts[3]);
						record.put("instances", Integer.parseInt(parts[1]));
						record.put("bytes", Integer.parseInt(parts[2]));
						records.add(record);
					}
				}
			}
			return root;
		}
	}
}
