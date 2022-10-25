/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.apm;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VerificationResponseParserTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testProcess_WhenRegexIsDefinedForHost() throws IOException {
    List<Multimap<String, Object>> output = new ArrayList<>();
    VerificationResponseParser.process(getVerificationResponse(), getDatadogLogJsonObject(), 0, output);
    assertThat(JsonUtils.asJson(output)).isEqualTo(JsonUtils.asJson(getOutputMultimap()));
  }

  private JSONObject getDatadogLogJsonObject() throws IOException {
    String datadogLogResponse = Resources.toString(
        VerificationResponseParserTest.class.getResource("/apm/datadog_log_json_response.json"), Charsets.UTF_8);
    return new JSONObject(datadogLogResponse);
  }

  private VerificationResponseParser getVerificationResponse() {
    String verificationResponseParser = "{\n"
        + "  \"children\": {\n"
        + "    \"logs[*]\": {\n"
        + "      \"children\": {\n"
        + "        \"content\": {\n"
        + "          \"children\": {\n"
        + "            \"message\": {\n"
        + "              \"value\": [\n"
        + "                \"logMessage\"\n"
        + "              ],\n"
        + "              \"children\": {},\n"
        + "              \"regex\": [\n"
        + "                []\n"
        + "              ]\n"
        + "            },\n"
        + "            \"tags\": {\n"
        + "              \"children\": {\n"
        + "                \"[*]\": {\n"
        + "                  \"value\": [\n"
        + "                    \"host\"\n"
        + "                  ],\n"
        + "                  \"children\": {},\n"
        + "                  \"regex\": [\n"
        + "                    [\n"
        + "                      \"((?<=pod_name:)(.*))\"\n"
        + "                    ]\n"
        + "                  ]\n"
        + "                }\n"
        + "              }\n"
        + "            },\n"
        + "            \"timestamp\": {\n"
        + "              \"value\": [\n"
        + "                \"timestamp\"\n"
        + "              ],\n"
        + "              \"children\": {},\n"
        + "              \"regex\": [\n"
        + "                []\n"
        + "              ]\n"
        + "            }\n"
        + "          }\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}";
    return new Gson().fromJson(verificationResponseParser, VerificationResponseParser.class);
  }

  private List<Multimap<String, Object>> getOutputMultimap() {
    String outputJson = "[\n"
        + "  {\n"
        + "    \"logMessage\": [\n"
        + "      \"</pre><p><b>Note</b> The full stack trace\"\n"
        + "    ],\n"
        + "    \"host\": [\n"
        + "      \"harness-example-deployment-canary-68659cc85f-szjs7\",\n"
        + "      \"harness-example-deployment-canary-68659cc85f-szjs7\"\n"
        + "    ],\n"
        + "    \"timestamp\": [\n"
        + "      \"2020-03-16T08:00:59.409Z\"\n"
        + "    ]\n"
        + "  },\n"
        + "  {\n"
        + "    \"logMessage\": [\n"
        + "      \"> GET /todolist/exception HTTP/1.1\"\n"
        + "    ],\n"
        + "    \"host\": [\n"
        + "      \"harness-example-deployment-canary-68659cc85f-szjs7\",\n"
        + "      \"harness-example-deployment-canary-68659cc85f-szjs7\"\n"
        + "    ],\n"
        + "    \"timestamp\": [\n"
        + "      \"2020-03-16T08:00:59.407Z\"\n"
        + "    ]\n"
        + "  }\n"
        + "]";
    return new Gson().fromJson(outputJson, List.class);
  }
}
