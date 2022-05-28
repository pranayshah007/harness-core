/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.StackdriverDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverMetricHealthSourceSpec;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StackdriverMetricHealthSourceSpecTest {
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String connectorIdentifier;
  String serviceIdentifier;
  String envIdentifier;
  String identifier;
  String name;
  String monitoredServiceIdentifier;
  String metricName;
  BuilderFactory builderFactory;
  StackdriverMetricHealthSourceSpec stackdriverMetricHealthSourceSpec;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    connectorIdentifier = "connectorRef";
    metricName = "sampleMetric";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    stackdriverMetricHealthSourceSpec =
        StackdriverMetricHealthSourceSpec.builder().connectorRef(connectorIdentifier).build();
  }
  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_withAnalysisInfo() {
    StackdriverDefinition metricDefinition =
        StackdriverDefinition.builder()
            .metricName(metricName)
            .identifier(metricName)
            .dashboardName("Delegate Tasks - prod")
            .dashboardPath("projects/778566137835/dashboards/861a44fb-7d3f-4cf9-b945-765121fe14eb")
            .isManualQuery(Boolean.FALSE)
            .serviceInstanceField(serviceIdentifier)
            .analysis(
                HealthSourceMetricDefinition.AnalysisDTO.builder()
                    .liveMonitoring(HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
                                        .enabled(Boolean.TRUE)
                                        .build())
                    .deploymentVerification(HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                                .enabled(Boolean.TRUE)
                                                .serviceInstanceFieldName("pod")
                                                .build())
                    .riskProfile(RiskProfile.builder()
                                     .metricType(TimeSeriesMetricType.RESP_TIME)
                                     .category(CVMonitoringCategory.PERFORMANCE)
                                     .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                                     .build())
                    .build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(Boolean.TRUE).build())
            .build();
    stackdriverMetricHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition));

    HealthSource.CVConfigUpdateResult cvConfigUpdateResult = stackdriverMetricHealthSourceSpec.getCVConfigUpdateResult(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier,
        identifier, name, Collections.emptyList(), null);
    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getAdded().size()).isEqualTo(1);
    List<StackdriverCVConfig> cvConfigList = (List<StackdriverCVConfig>) (List<?>) cvConfigUpdateResult.getAdded();
    StackdriverCVConfig cvConfig = cvConfigList.get(0);
    assertThat(cvConfig.getMetricInfoList().size()).isEqualTo(1);
    StackdriverCVConfig.MetricInfo metricInfo = cvConfig.getMetricInfoList().get(0);
    assertThat(metricInfo.getMetricName()).isEqualTo(metricName);
    assertThat(metricInfo.getIdentifier()).isEqualTo(metricName);
    assertThat(metricInfo.getLiveMonitoring().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled());
    assertThat(metricInfo.getDeploymentVerification().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled());
    assertThat(metricInfo.getServiceInstanceField())
        .isEqualTo(metricDefinition.getAnalysis().getDeploymentVerification().getServiceInstanceFieldName());
    assertThat(metricInfo.getMetricType()).isEqualTo(metricDefinition.getAnalysis().getRiskProfile().getMetricType());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_withAnalysisInfoWithBackwardCompatibility() {
    StackdriverDefinition metricDefinition =
        StackdriverDefinition.builder()
            .metricName(metricName)
            .identifier(metricName)
            .dashboardName("Delegate Tasks - prod")
            .dashboardPath("projects/778566137835/dashboards/861a44fb-7d3f-4cf9-b945-765121fe14eb")
            .isManualQuery(Boolean.FALSE)
            .serviceInstanceField(serviceIdentifier)
            .analysis(
                HealthSourceMetricDefinition.AnalysisDTO.builder()
                    .liveMonitoring(HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder()
                                        .enabled(Boolean.TRUE)
                                        .build())
                    .deploymentVerification(HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                                .enabled(Boolean.TRUE)
                                                .build())
                    .riskProfile(RiskProfile.builder()
                                     .metricType(TimeSeriesMetricType.RESP_TIME)
                                     .category(CVMonitoringCategory.PERFORMANCE)
                                     .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                                     .build())
                    .build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(Boolean.TRUE).build())
            .build();
    stackdriverMetricHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition));

    HealthSource.CVConfigUpdateResult cvConfigUpdateResult = stackdriverMetricHealthSourceSpec.getCVConfigUpdateResult(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, monitoredServiceIdentifier,
        identifier, name, Collections.emptyList(), null);
    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getAdded().size()).isEqualTo(1);
    List<StackdriverCVConfig> cvConfigList = (List<StackdriverCVConfig>) (List<?>) cvConfigUpdateResult.getAdded();
    StackdriverCVConfig cvConfig = cvConfigList.get(0);
    assertThat(cvConfig.getMetricInfoList().size()).isEqualTo(1);
    StackdriverCVConfig.MetricInfo metricInfo = cvConfig.getMetricInfoList().get(0);
    assertThat(metricInfo.getMetricName()).isEqualTo(metricName);
    assertThat(metricInfo.getLiveMonitoring().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled());
    assertThat(metricInfo.getDeploymentVerification().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled());
    assertThat(metricInfo.getMetricType()).isEqualTo(metricDefinition.getRiskProfile().getMetricType());
  }
}
