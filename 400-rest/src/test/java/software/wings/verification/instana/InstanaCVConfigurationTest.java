/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification.instana;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.instana.InstanaDataCollectionInfo;
import software.wings.service.impl.instana.InstanaTagFilter;
import software.wings.sm.StateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InstanaCVConfigurationTest extends WingsBaseTest {
  private static final String configName = "configName";
  private static final String accountId = "accountId";
  private static final String connectorId = "connectorId";
  private static final String envId = "envId";
  private static final String serviceId = "serviceId";
  private static final StateType stateType = StateType.INSTANA;
  private List<InstanaTagFilter> instanaTagFilters;

  private InstanaCVConfiguration createInstanaCVConfig() {
    InstanaCVConfiguration config = new InstanaCVConfiguration();
    config.setName(configName);
    config.setAccountId(accountId);
    config.setConnectorId(connectorId);
    config.setEnvId(envId);
    config.setServiceId(serviceId);
    config.setStateType(stateType);
    config.setEnabled24x7(true);
    instanaTagFilters = new ArrayList<>();
    instanaTagFilters.add(InstanaTagFilter.builder()
                              .name("kubernetes.cluster.name")
                              .operator(InstanaTagFilter.Operator.EQUALS)
                              .value("harness-test")
                              .build());
    config.setTagFilters(instanaTagFilters);

    return config;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCloneInstanaConfig() {
    InstanaCVConfiguration config = createInstanaCVConfig();

    InstanaCVConfiguration clonedConfig = (InstanaCVConfiguration) config.deepCopy();

    assertThat(clonedConfig.getName()).isEqualTo(configName);
    assertThat(clonedConfig.getAccountId()).isEqualTo(accountId);
    assertThat(clonedConfig.getConnectorId()).isEqualTo(connectorId);
    assertThat(clonedConfig.getEnvId()).isEqualTo(envId);
    assertThat(clonedConfig.getServiceId()).isEqualTo(serviceId);
    assertThat(clonedConfig.getStateType()).isEqualTo(stateType);
    assertThat(clonedConfig.isEnabled24x7()).isTrue();
    assertThat(clonedConfig.getTagFilters()).isEqualTo(config.getTagFilters());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testToDataCollectionInfo() {
    InstanaCVConfiguration instanaCVConfig = createInstanaCVConfig();
    InstanaDataCollectionInfo dataCollectionInfo = (InstanaDataCollectionInfo) instanaCVConfig.toDataCollectionInfo();
    assertThat(dataCollectionInfo.getAccountId()).isEqualTo(instanaCVConfig.getAccountId());
    assertThat(dataCollectionInfo.getCvConfigId()).isEqualTo(instanaCVConfig.getUuid());
    assertThat(dataCollectionInfo.getStateExecutionId())
        .isEqualTo(CV_24x7_STATE_EXECUTION + "-" + instanaCVConfig.getUuid());
    assertThat(dataCollectionInfo).isInstanceOf(InstanaDataCollectionInfo.class);
    assertThat(dataCollectionInfo.getStartTime()).isNull();
    assertThat(dataCollectionInfo.getEndTime()).isNull();
    assertThat(dataCollectionInfo.getTagFilters()).isEqualTo(instanaCVConfig.getTagFilters());
    assertThat(dataCollectionInfo.getMetrics()).isEmpty();
    assertThat(dataCollectionInfo.getHostTagFilter()).isNull();
    assertThat(dataCollectionInfo.getQuery()).isNull();
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    assertThat(dataCollectionInfo.getHostsToGroupNameMap()).isEqualTo(hostsMap);
  }
}
