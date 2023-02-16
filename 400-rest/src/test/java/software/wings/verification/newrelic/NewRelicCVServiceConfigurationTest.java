/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification.newrelic;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;
import software.wings.sm.StateType;

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class NewRelicCVServiceConfigurationTest extends WingsBaseTest {
  private static final String configName = "configName";
  private static final String accountId = "accountId";
  private static final String connectorId = "connectorId";
  private static final String envId = "envId";
  private static final String serviceId = "serviceId";
  private static final StateType stateType = StateType.NEW_RELIC;

  private static final long newRelicAppId = 1020933;

  private List<String> getMetrics() {
    return Lists.newArrayList("metric1", "metric2");
  }

  private NewRelicCVServiceConfiguration createNewRelicConfig() {
    NewRelicCVServiceConfiguration config = new NewRelicCVServiceConfiguration();
    config.setName(configName);
    config.setAccountId(accountId);
    config.setConnectorId(connectorId);
    config.setEnvId(envId);
    config.setServiceId(serviceId);
    config.setStateType(stateType);
    config.setEnabled24x7(true);
    config.setApplicationId(String.valueOf(newRelicAppId));
    config.setMetrics(getMetrics());

    return config;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCloneNewRelicConfig() {
    NewRelicCVServiceConfiguration config = createNewRelicConfig();

    NewRelicCVServiceConfiguration clonedConfig = (NewRelicCVServiceConfiguration) config.deepCopy();

    assertThat(clonedConfig.getName()).isEqualTo(configName);
    assertThat(clonedConfig.getAccountId()).isEqualTo(accountId);
    assertThat(clonedConfig.getConnectorId()).isEqualTo(connectorId);
    assertThat(clonedConfig.getEnvId()).isEqualTo(envId);
    assertThat(clonedConfig.getServiceId()).isEqualTo(serviceId);
    assertThat(clonedConfig.getStateType()).isEqualTo(stateType);
    assertThat(clonedConfig.isEnabled24x7()).isTrue();
    assertThat(clonedConfig.getApplicationId()).isEqualTo(String.valueOf(newRelicAppId));
    assertThat(new HashSet<>(clonedConfig.getMetrics())).isEqualTo(new HashSet<>(getMetrics()));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testNewRelicDataCollectionInfoCreation() {
    NewRelicCVServiceConfiguration newRelicCVConfig = createNewRelicConfig();
    NewRelicDataCollectionInfoV2 dataCollectionInfo =
        (NewRelicDataCollectionInfoV2) newRelicCVConfig.toDataCollectionInfo();
    assertThat(dataCollectionInfo.getAccountId()).isEqualTo(newRelicCVConfig.getAccountId());
    assertThat(dataCollectionInfo.getCvConfigId()).isEqualTo(newRelicCVConfig.getUuid());
    assertThat(dataCollectionInfo.getStateExecutionId())
        .isEqualTo(CV_24x7_STATE_EXECUTION + "-" + newRelicCVConfig.getUuid());
    assertThat(dataCollectionInfo instanceof NewRelicDataCollectionInfoV2).isTrue();
    assertThat(dataCollectionInfo.getStartTime()).isNull();
    assertThat(dataCollectionInfo.getEndTime()).isNull();
    assertThat(dataCollectionInfo.getNewRelicAppId()).isEqualTo(newRelicAppId);
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    assertThat(dataCollectionInfo.getHostsToGroupNameMap()).isEqualTo(hostsMap);
  }
}
