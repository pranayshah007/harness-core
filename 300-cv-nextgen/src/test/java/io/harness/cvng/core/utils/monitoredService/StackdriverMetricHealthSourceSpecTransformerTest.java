/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import static io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdCriteriaType.ABSOLUTE;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.beans.StackdriverDefinition;
import io.harness.cvng.core.beans.monitoredService.TimeSeriesMetricPackDTO;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverMetricHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.metricThresholdSpec.MetricThresholdActionType;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;
import io.harness.cvng.core.entities.AnalysisInfo.LiveMonitoring;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.StackdriverCVConfig;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class StackdriverMetricHealthSourceSpecTransformerTest extends CvNextGenTestBase {
  String connectorIdentifier;
  String identifier;
  String monitoringSourceName;
  BuilderFactory builderFactory;
  String metricName;
  String metricGroupName;
  String customIdentifier;

  @Inject StackdriverMetricHealthSourceSpecTransformer stackdriverMetricHealthSourceSpecTransformer;
  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    connectorIdentifier = "connectorId";
    identifier = "healthSourceIdentifier";
    monitoringSourceName = "Prometheus";
    metricName = "MetricName";
    metricGroupName = "MetricGroupName";
    customIdentifier = "Custom";
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testTransformToHealthSourceConfig() throws Exception {
    StackdriverMetricHealthSourceSpec spec =
        stackdriverMetricHealthSourceSpecTransformer.transform(Arrays.asList(createCVConfig()));
    String metricDef = Resources.toString(
        StackdriverMetricHealthSourceSpecTransformerTest.class.getResource("/stackdriver/metric-definition.json"),
        Charsets.UTF_8);

    assertThat(spec).isNotNull();
    assertThat(spec.getConnectorRef()).isEqualTo(connectorIdentifier);
    assertThat(spec.getMetricDefinitions().size()).isEqualTo(1);
    StackdriverDefinition metricDefinition = spec.getMetricDefinitions().get(0);
    assertThat(metricDefinition.getMetricName()).isEqualTo("metricName1");
    assertThat(metricDefinition.getJsonMetricDefinition()).isEqualTo(JsonUtils.asObject(metricDef, Object.class));
    assertThat(metricDefinition.getRiskProfile().getCategory()).isEqualTo(CVMonitoringCategory.PERFORMANCE);
    assertThat(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled()).isTrue();
    assertThat(metricDefinition.getSli().getEnabled()).isTrue();
    assertThat(metricDefinition.getAnalysis().getRiskProfile().getCategory())
        .isEqualTo(CVMonitoringCategory.PERFORMANCE);
    assertThat(spec.getMetricPacks()).hasSize(1);
    TimeSeriesMetricPackDTO timeSeriesMetricPackDTO = spec.getMetricPacks().iterator().next();
    assertThat(timeSeriesMetricPackDTO.getMetricThresholds()).hasSize(1);
    assertThat(timeSeriesMetricPackDTO.getIdentifier()).isEqualTo(customIdentifier);
    TimeSeriesMetricPackDTO.MetricThreshold metricThreshold = timeSeriesMetricPackDTO.getMetricThresholds().get(0);
    assertThat(metricThreshold.getMetricType()).isEqualTo(customIdentifier);
    assertThat(metricThreshold.getMetricName()).isEqualTo(metricName);
    assertThat(metricThreshold.getGroupName()).isEqualTo(metricGroupName);
    assertThat(metricThreshold.getType()).isEqualTo(MetricThresholdActionType.IGNORE);
    assertThat(metricThreshold.getCriteria().getType()).isEqualTo(ABSOLUTE);
    assertThat(metricThreshold.getCriteria().getSpec().getLessThan()).isEqualTo(20.0);
  }

  private StackdriverCVConfig createCVConfig() throws IOException {
    StackdriverCVConfig cvConfig = builderFactory.stackdriverMetricCVConfigBuilder().build();
    cvConfig.setConnectorIdentifier(connectorIdentifier);
    String metricDef = Resources.toString(
        StackdriverMetricHealthSourceSpecTransformerTest.class.getResource("/stackdriver/metric-definition.json"),
        Charsets.UTF_8);

    StackdriverCVConfig.MetricInfo metricInfo =
        StackdriverCVConfig.MetricInfo.builder().metricName("metricName1").jsonMetricDefinition(metricDef).build();
    metricInfo.setDeploymentVerification(DeploymentVerification.builder().enabled(Boolean.TRUE).build());
    metricInfo.setLiveMonitoring(LiveMonitoring.builder().enabled(Boolean.TRUE).build());
    metricInfo.setSli(SLI.builder().enabled(Boolean.TRUE).build());
    cvConfig.setMetricPack(MetricPack.builder()
                               .category(CVMonitoringCategory.PERFORMANCE)
                               .metrics(Collections.singleton(
                                   MetricPack.MetricDefinition.builder()
                                       .name(metricName)
                                       .thresholds(Collections.singletonList(
                                           builderFactory.getMetricThresholdBuilder(metricName, metricGroupName)))
                                       .build()))
                               .build());
    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    return cvConfig;
  }
}
