/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import io.specto.hoverfly.junit.core.HoverflyConfig;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@Slf4j
@RunWith(Suite.class)
@SuiteClasses({AppDynamicsDataCollectionDSLTestSuiteChild.class, AwsPrometheusDataCollectionDSLTestSuiteChild.class,
    AzureLogsDataCollectionDSLTestSuiteChild.class, AzureMetricsDataCollectionDSLTestSuiteChild.class,
    CloudWatchMetricsDataCollectionDSLTestSuiteChild.class, CustomHealthMetricDataCollectionDSLTestSuiteChild.class,
    CustomHealthLogDataCollectionDSLTestSuiteChild.class, DatadogLogDataCollectionDSLTestSuiteChild.class,
    DatadogMetricDataCollectionDSLTestSuiteChild.class, DatadogMetricDataCollectionDSLV2TestSuiteChild.class,
    DynatraceDataCollectionDSLTestSuiteChild.class, ELKDataCollectionDSLTestSuiteChild.class,
    GrafanaLokiLogDSLTestSuiteChild.class, HoverflyExampleTestSuiteChild.class,
    NewRelicDataCollectionDSLTestSuiteChild.class, PagerDutyDataCollectionDSLTestSuiteChild.class,
    PrometheusDataCollectionDSLTestSuiteChild.class, PrometheusDataCollectionDSLV2TestSuiteChild.class,
    SignalFXMetricDataCollectionDSLTestSuiteChild.class, SplunkDataCollectionDSLTestSuiteChild.class,
    SplunkMetricDataCollectionDSLTestSuiteChild.class, StackdriverLogDataCollectionDSLTestSuiteChild.class,
    StackdriverMetricDataCollectionDSLTestSuiteChild.class, SumologicLogDataCollectionDSLTestSuiteChild.class,
    SumologicMetricDataCollectionDSLTestSuiteChild.class})
public class DSLSuiteTest {
  @ClassRule public static HoverflyRule HOVERFLY_RULE;

  public static boolean testSuiteStarted;

  /*
To Run Hoverfly in Capture mode and change hoverflyMode and projectBaseFolder below
and "--strategy=TestRunner=local" to bazel flags for run time to not run it in sandbox mode
 */
  public static HoverflyMode hoverflyMode = HoverflyMode.SIMULATE;

  static {
    try {
      if (hoverflyMode.equals(HoverflyMode.SIMULATE)) {
        HOVERFLY_RULE = HoverflyRule.inSimulationMode(HoverflyConfig.localConfigs().disableTlsVerification());
      } else {
        HOVERFLY_RULE = HoverflyRule.inCaptureMode(HoverflyConfig.localConfigs().disableTlsVerification());
      }
    } catch (Exception e) {
      log.info("Initializing HoverflyRule inSimulationMode failed. Retrying one more time : {}", e);
      // This is rarely failing in CI with port conflict exception. So retrying one more time.
      // If you still face this issue in your PR's please notify me(kamal).
      HOVERFLY_RULE = HoverflyRule.inSimulationMode(HoverflyConfig.localConfigs().disableTlsVerification());
    }
  }

  @BeforeClass
  public static void beforeClass() {
    testSuiteStarted = true;
  }
}
