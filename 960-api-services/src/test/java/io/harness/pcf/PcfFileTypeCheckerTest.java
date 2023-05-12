/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.pcf.model.ManifestType.APPLICATION_MANIFEST;
import static io.harness.pcf.model.ManifestType.VARIABLE_MANIFEST;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class PcfFileTypeCheckerTest extends CategoryTest {
  private String MANIFEST_YML = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : 2\n"
      + "  random-route: true\n";

  private String NO_YML = "";

  private String INVALID_MANIFEST_YML = "applications:\n"
      + "  - name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : 2\n"
      + "  random-route: true\n";

  private String MANIFEST_YML_NO_MEM = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  instances : 2\n"
      + "  random-route: true\n";

  private String MANIFEST_YML_CREATE_SERVICE_PUSH_YML = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances : 2\n"
      + "  random-route: true\n"
      + "create-services:\n"
      + "- name:   \"my-database-service\"\n"
      + "  broker: \"p-mysql\"\n"
      + "  plan:   \"1gb\"\n"
      + "\n"
      + "- name:   \"Another-service\"\n"
      + "  broker: \"p-brokerName\"\n"
      + "  plan:   \"sharedPlan\"\n"
      + "  parameters: \"{\\\"RAM\\\": 4gb }\"";

  private String CREATE_SERVICE_PUSH_YML = "create-services:\n"
      + "- name:   \"my-database-service\"\n"
      + "  broker: \"p-mysql\"\n"
      + "  plan:   \"1gb\"\n"
      + "\n"
      + "- name:   \"Another-service\"\n"
      + "  broker: \"p-brokerName\"\n"
      + "  plan:   \"sharedPlan\"\n"
      + "  parameters: \"{\\\"RAM\\\": 4gb }\"";

  private String TEST_VAR = "  MY: order\n"
      + "  PCF_APP_NAME : prod";

  private String NOT_VAR = "APP_NAME:${app.name}__${service.name}__${env.name}\n"
      + "APP_MEMORY:750M\n"
      + "INSTANCES:2";

  private String AUTOSCALAR_MANIFEST = "---\n"
      + "instance_limits:\n"
      + "  min: 6\n"
      + "  max: 12\n"
      + "rules:\n"
      + "- rule_type: \"http_latency\"\n"
      + "  rule_sub_type: \"avg_99th\"\n"
      + "  threshold:\n"
      + "    min: 10\n"
      + "    max: 20\n"
      + "scheduled_limit_changes:\n"
      + "- recurrence: 32\n"
      + "  executes_at: \"2032-01-01T20:00:00Z\"\n"
      + "  instance_limits:\n"
      + "    min: 1\n"
      + "    max: 3\n"
      + "- recurrence: 2\n"
      + "  executes_at: \"2032-01-01T04:00:00Z\"\n"
      + "  instance_limits:\n"
      + "    min: 6\n"
      + "    max: 12";

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCategory() {
    PcfFileTypeChecker pcfFileTypeChecker = new PcfFileTypeChecker();
    LogCallback logCallback = Mockito.mock(LogCallback.class);
    assertThat(pcfFileTypeChecker.getManifestType(MANIFEST_YML, null, logCallback)).isEqualTo(APPLICATION_MANIFEST);
    assertThat(pcfFileTypeChecker.getManifestType(MANIFEST_YML, null, logCallback)).isEqualTo(APPLICATION_MANIFEST);
    assertThat(pcfFileTypeChecker.getManifestType(MANIFEST_YML_NO_MEM, null, logCallback))
        .isEqualTo(APPLICATION_MANIFEST);
    assertThat(pcfFileTypeChecker.getManifestType(TEST_VAR, null, logCallback)).isEqualTo(VARIABLE_MANIFEST);
    assertThat(pcfFileTypeChecker.getManifestType(NOT_VAR, null, logCallback)).isNotEqualTo(VARIABLE_MANIFEST);
    assertThat(pcfFileTypeChecker.getManifestType(MANIFEST_YML, null, logCallback)).isNotEqualTo(VARIABLE_MANIFEST);
    assertThat(pcfFileTypeChecker.getManifestType(AUTOSCALAR_MANIFEST, null, logCallback))
        .isNotEqualTo(AUTOSCALAR_MANIFEST);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCategoryFail() {
    PcfFileTypeChecker pcfFileTypeChecker = new PcfFileTypeChecker();
    LogCallback logCallback = Mockito.mock(LogCallback.class);
    assertThat(pcfFileTypeChecker.getManifestType(INVALID_MANIFEST_YML, null, logCallback)).isEqualTo(null);
    assertThat(pcfFileTypeChecker.getManifestType(NO_YML, null, logCallback)).isEqualTo(null);
    Mockito.verify(logCallback, Mockito.times(4)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }
}
