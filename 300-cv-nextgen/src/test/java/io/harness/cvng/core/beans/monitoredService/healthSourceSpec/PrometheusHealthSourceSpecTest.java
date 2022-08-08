/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.beans.TimeSeriesThresholdType;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource.CVConfigUpdateResult;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.PrometheusHealthSourceSpec;
import io.harness.cvng.core.entities.PrometheusCVConfig;
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
public class PrometheusHealthSourceSpecTest extends CvNextGenTestBase {
  String orgIdentifier;
  String projectIdentifier;
  String accountId;
  String connectorIdentifier;
  String serviceIdentifier;
  String envIdentifier;
  String identifier;
  String name;
  String monitoredServiceIdentifier;
  BuilderFactory builderFactory;
  PrometheusHealthSourceSpec prometheusHealthSourceSpec;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    connectorIdentifier = "connectorRef";
    monitoredServiceIdentifier = generateUuid();
    identifier = "identifier";
    name = "some-name";
    prometheusHealthSourceSpec = PrometheusHealthSourceSpec.builder().connectorRef(connectorIdentifier).build();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenNoConfigExists() {
    PrometheusMetricDefinition metricDefinition =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric")
            .identifier("sampleMetric")
            .groupName("myMetricGroupName")
            .prometheusMetric("container.cpu.usage.total")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .serviceInstanceFieldName("pod")
            .riskProfile(RiskProfile.builder()
                             .metricType(TimeSeriesMetricType.RESP_TIME)
                             .category(CVMonitoringCategory.PERFORMANCE)
                             .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                             .build())
            .build();
    prometheusHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition));

    CVConfigUpdateResult cvConfigUpdateResult =
        prometheusHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, Collections.emptyList(), null);
    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getAdded().size()).isEqualTo(1);
    List<PrometheusCVConfig> cvConfigList = (List<PrometheusCVConfig>) (List<?>) cvConfigUpdateResult.getAdded();
    PrometheusCVConfig cvConfig = cvConfigList.get(0);
    assertThat(cvConfig.getGroupName()).isEqualTo("myMetricGroupName");
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(cvConfig.getMetricInfoList().size()).isEqualTo(1);
    PrometheusCVConfig.MetricInfo metricInfo = cvConfig.getMetricInfoList().get(0);
    assertThat(metricInfo.getMetricName()).isEqualTo("sampleMetric");
    assertThat(metricInfo.getPrometheusMetricName()).isEqualTo(metricDefinition.getPrometheusMetric());
    assertThat(metricInfo.getEnvFilter()).isEqualTo(metricDefinition.getEnvFilter());
    assertThat(metricInfo.getServiceFilter()).isEqualTo(metricDefinition.getServiceFilter());
    assertThat(metricInfo.getServiceInstanceFieldName()).isEqualTo(metricDefinition.getServiceInstanceFieldName());
    assertThat(metricInfo.getAggregation()).isEqualTo(metricDefinition.getAggregation());
    assertThat(metricInfo.getAdditionalFilters()).isEqualTo(metricDefinition.getAdditionalFilters());
    assertThat(metricInfo.getIdentifier()).isEqualTo("sampleMetric");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenNoConfigExists2ItemsSameGroup() {
    PrometheusMetricDefinition metricDefinition =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric")
            .identifier("sampleMetric")
            .groupName("myMetricGroupName")
            .prometheusMetric("container.cpu.usage.total")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .serviceInstanceFieldName("pod")
            .riskProfile(RiskProfile.builder()
                             .metricType(TimeSeriesMetricType.RESP_TIME)
                             .category(CVMonitoringCategory.PERFORMANCE)
                             .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                             .build())
            .build();

    PrometheusMetricDefinition metricDefinition2 =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric2")
            .groupName("myMetricGroupName")
            .prometheusMetric("container.cpu.usage")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .serviceInstanceFieldName("pod")
            .riskProfile(RiskProfile.builder()
                             .metricType(TimeSeriesMetricType.THROUGHPUT)
                             .category(CVMonitoringCategory.PERFORMANCE)
                             .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                             .build())
            .build();
    prometheusHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition, metricDefinition2));
    CVConfigUpdateResult cvConfigUpdateResult =
        prometheusHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, Collections.emptyList(), null);
    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getAdded().size()).isEqualTo(1);
    List<PrometheusCVConfig> cvConfigList = (List<PrometheusCVConfig>) (List<?>) cvConfigUpdateResult.getAdded();
    PrometheusCVConfig cvConfig = cvConfigList.get(0);
    assertThat(cvConfig.getGroupName()).isEqualTo("myMetricGroupName");
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(cvConfig.getCategory().name()).isEqualTo(CVMonitoringCategory.PERFORMANCE.name());
    assertThat(cvConfig.getMetricInfoList().size()).isEqualTo(2);
    PrometheusCVConfig.MetricInfo metricInfo = cvConfig.getMetricInfoList().get(0);
    assertThat(metricInfo.getMetricName()).isEqualTo("sampleMetric");
    assertThat(metricInfo.getPrometheusMetricName()).isEqualTo(metricDefinition.getPrometheusMetric());
    assertThat(metricInfo.getEnvFilter()).isEqualTo(metricDefinition.getEnvFilter());
    assertThat(metricInfo.getServiceFilter()).isEqualTo(metricDefinition.getServiceFilter());
    assertThat(metricInfo.getServiceInstanceFieldName()).isEqualTo(metricDefinition.getServiceInstanceFieldName());
    assertThat(metricInfo.getAggregation()).isEqualTo(metricDefinition.getAggregation());
    assertThat(metricInfo.getAdditionalFilters()).isEqualTo(metricDefinition.getAdditionalFilters());

    metricInfo = cvConfig.getMetricInfoList().get(1);
    assertThat(metricInfo.getMetricName()).isEqualTo(metricDefinition2.getMetricName());
    assertThat(metricInfo.getPrometheusMetricName()).isEqualTo(metricDefinition2.getPrometheusMetric());
    assertThat(metricInfo.getEnvFilter()).isEqualTo(metricDefinition2.getEnvFilter());
    assertThat(metricInfo.getServiceFilter()).isEqualTo(metricDefinition2.getServiceFilter());
    assertThat(metricInfo.getServiceInstanceFieldName()).isEqualTo(metricDefinition2.getServiceInstanceFieldName());
    assertThat(metricInfo.getAggregation()).isEqualTo(metricDefinition2.getAggregation());
    assertThat(metricInfo.getAdditionalFilters()).isEqualTo(metricDefinition2.getAdditionalFilters());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenUpdated() {
    PrometheusMetricDefinition metricDefinition2 =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric2")
            .identifier("identifier")
            .groupName("groupName")
            .prometheusMetric("container.cpu.usage")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .serviceInstanceFieldName("pod")
            .riskProfile(RiskProfile.builder()
                             .metricType(TimeSeriesMetricType.THROUGHPUT)
                             .category(CVMonitoringCategory.PERFORMANCE)
                             .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                             .build())
            .build();
    prometheusHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition2));

    CVConfigUpdateResult cvConfigUpdateResult =
        prometheusHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, Arrays.asList(createCVConfig()), null);

    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getUpdated().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_whenDeleted() {
    PrometheusMetricDefinition metricDefinition2 =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric2")
            .identifier("identifier")
            .groupName("groupNameNew")
            .prometheusMetric("container.cpu.usage")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .serviceInstanceFieldName("pod")
            .riskProfile(RiskProfile.builder()
                             .metricType(TimeSeriesMetricType.THROUGHPUT)
                             .category(CVMonitoringCategory.PERFORMANCE)
                             .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                             .build())
            .build();
    prometheusHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition2));

    CVConfigUpdateResult cvConfigUpdateResult =
        prometheusHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, Arrays.asList(createCVConfig()), null);

    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isNotEmpty();

    assertThat(cvConfigUpdateResult.getDeleted().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_withAnalysisInfo() {
    PrometheusMetricDefinition metricDefinition =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric")
            .identifier("sampleMetric")
            .groupName("myMetricGroupName")
            .prometheusMetric("container.cpu.usage.total")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .analysis(AnalysisDTO.builder()
                          .liveMonitoring(LiveMonitoringDTO.builder().enabled(Boolean.TRUE).build())
                          .deploymentVerification(DeploymentVerificationDTO.builder()
                                                      .enabled(Boolean.TRUE)
                                                      .serviceInstanceFieldName("pod")
                                                      .build())
                          .riskProfile(RiskProfile.builder()
                                           .metricType(TimeSeriesMetricType.RESP_TIME)
                                           .category(CVMonitoringCategory.PERFORMANCE)
                                           .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                                           .build())
                          .build())
            .sli(SLIDTO.builder().enabled(Boolean.TRUE).build())
            .build();
    prometheusHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition));

    CVConfigUpdateResult cvConfigUpdateResult =
        prometheusHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, Collections.emptyList(), null);
    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getAdded().size()).isEqualTo(1);
    List<PrometheusCVConfig> cvConfigList = (List<PrometheusCVConfig>) (List<?>) cvConfigUpdateResult.getAdded();
    PrometheusCVConfig cvConfig = cvConfigList.get(0);
    assertThat(cvConfig.getGroupName()).isEqualTo("myMetricGroupName");
    assertThat(cvConfig.getMonitoredServiceIdentifier()).isEqualTo(monitoredServiceIdentifier);
    assertThat(cvConfig.getMetricInfoList().size()).isEqualTo(1);
    PrometheusCVConfig.MetricInfo metricInfo = cvConfig.getMetricInfoList().get(0);
    assertThat(metricInfo.getMetricName()).isEqualTo("sampleMetric");
    assertThat(metricInfo.getPrometheusMetricName()).isEqualTo(metricDefinition.getPrometheusMetric());
    assertThat(metricInfo.getEnvFilter()).isEqualTo(metricDefinition.getEnvFilter());
    assertThat(metricInfo.getServiceFilter()).isEqualTo(metricDefinition.getServiceFilter());
    assertThat(metricInfo.getServiceInstanceFieldName()).isEqualTo(metricDefinition.getServiceInstanceFieldName());
    assertThat(metricInfo.getAggregation()).isEqualTo(metricDefinition.getAggregation());
    assertThat(metricInfo.getAdditionalFilters()).isEqualTo(metricDefinition.getAdditionalFilters());
    assertThat(metricInfo.getLiveMonitoring().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled());
    assertThat(metricInfo.getDeploymentVerification().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled());
    assertThat(metricInfo.getServiceInstanceFieldName())
        .isEqualTo(metricDefinition.getAnalysis().getDeploymentVerification().getServiceInstanceFieldName());
    assertThat(metricInfo.getMetricType()).isEqualTo(metricDefinition.getAnalysis().getRiskProfile().getMetricType());
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void getCVConfigUpdateResult_withAnalysisInfoWithBackwardCompatibility() {
    PrometheusMetricDefinition metricDefinition =
        PrometheusMetricDefinition.builder()
            .metricName("sampleMetric")
            .identifier("sampleMetric")
            .groupName("myMetricGroupName")
            .prometheusMetric("container.cpu.usage.total")
            .serviceFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                             .labelName("namespace")
                                             .labelValue("cv-demo")
                                             .build()))
            .envFilter(Arrays.asList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                         .labelName("container")
                                         .labelValue("cv-demo")
                                         .build()))
            .serviceIdentifier(serviceIdentifier)
            .envIdentifier(envIdentifier)
            .riskProfile(RiskProfile.builder()
                             .metricType(TimeSeriesMetricType.RESP_TIME)
                             .category(CVMonitoringCategory.PERFORMANCE)
                             .thresholdTypes(Arrays.asList(TimeSeriesThresholdType.ACT_WHEN_HIGHER))
                             .build())
            .serviceInstanceFieldName("prod")
            .analysis(AnalysisDTO.builder()
                          .liveMonitoring(LiveMonitoringDTO.builder().enabled(Boolean.TRUE).build())
                          .deploymentVerification(DeploymentVerificationDTO.builder().enabled(Boolean.TRUE).build())

                          .build())
            .sli(SLIDTO.builder().enabled(Boolean.TRUE).build())
            .build();
    prometheusHealthSourceSpec.setMetricDefinitions(Arrays.asList(metricDefinition));

    CVConfigUpdateResult cvConfigUpdateResult =
        prometheusHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier, projectIdentifier, envIdentifier,
            serviceIdentifier, monitoredServiceIdentifier, identifier, name, Collections.emptyList(), null);
    assertThat(cvConfigUpdateResult).isNotNull();
    assertThat(cvConfigUpdateResult.getAdded()).isNotEmpty();
    assertThat(cvConfigUpdateResult.getUpdated()).isEmpty();
    assertThat(cvConfigUpdateResult.getDeleted()).isEmpty();

    assertThat(cvConfigUpdateResult.getAdded().size()).isEqualTo(1);
    List<PrometheusCVConfig> cvConfigList = (List<PrometheusCVConfig>) (List<?>) cvConfigUpdateResult.getAdded();
    PrometheusCVConfig cvConfig = cvConfigList.get(0);
    assertThat(cvConfig.getGroupName()).isEqualTo("myMetricGroupName");
    assertThat(cvConfig.getMetricInfoList().size()).isEqualTo(1);
    PrometheusCVConfig.MetricInfo metricInfo = cvConfig.getMetricInfoList().get(0);
    assertThat(metricInfo.getMetricName()).isEqualTo("sampleMetric");
    assertThat(metricInfo.getPrometheusMetricName()).isEqualTo(metricDefinition.getPrometheusMetric());
    assertThat(metricInfo.getEnvFilter()).isEqualTo(metricDefinition.getEnvFilter());
    assertThat(metricInfo.getServiceFilter()).isEqualTo(metricDefinition.getServiceFilter());
    assertThat(metricInfo.getServiceInstanceFieldName()).isEqualTo(metricDefinition.getServiceInstanceFieldName());
    assertThat(metricInfo.getAggregation()).isEqualTo(metricDefinition.getAggregation());
    assertThat(metricInfo.getAdditionalFilters()).isEqualTo(metricDefinition.getAdditionalFilters());
    assertThat(metricInfo.getLiveMonitoring().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getLiveMonitoring().getEnabled());
    assertThat(metricInfo.getDeploymentVerification().isEnabled())
        .isEqualTo(metricDefinition.getAnalysis().getDeploymentVerification().getEnabled());
    assertThat(metricInfo.getServiceInstanceFieldName()).isEqualTo(metricDefinition.getServiceInstanceFieldName());
    assertThat(metricInfo.getMetricType()).isEqualTo(metricDefinition.getRiskProfile().getMetricType());
  }

  private PrometheusCVConfig createCVConfig() {
    return (PrometheusCVConfig) builderFactory.prometheusCVConfigBuilder()
        .groupName("groupName")
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .build();
  }
}
