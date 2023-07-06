/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.PrometheusDataCollectionInfo;
import io.harness.cvng.beans.PrometheusDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition.PrometheusFilter;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ThresholdServiceLevelIndicator;
import io.harness.cvng.utils.PrometheusQueryUtils;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.cvng.prometheus.PrometheusUtils;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusDataCollectionInfoMapperTest extends CvNextGenTestBase {
  @Inject private PrometheusDataCollectionInfoMapper mapper;
  BuilderFactory builderFactory = BuilderFactory.getDefault();

  @Inject private MetricPackService metricPackService;

  FeatureFlagService featureFlagService;

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo =
        PrometheusCVConfig.MetricInfo.builder()
            .metricName("myMetric")
            .identifier("metricIdentifier")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .prometheusMetricName("cpu_usage_total")
            .envFilter(Arrays.asList(PrometheusFilter.builder().labelName("namespace").labelValue("cv-demo").build()))
            .serviceFilter(Arrays.asList(PrometheusFilter.builder().labelName("app").labelValue("cv-demo-app").build()))
            .additionalFilters(Arrays.asList(PrometheusFilter.builder().labelName("filter2").labelValue("cv-2").build(),
                PrometheusFilter.builder().labelName("filter3").labelValue("cv-3").build()))
            .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig, TaskType.DEPLOYMENT);
    assertThat(dataCollectionInfo.getGroupName()).isEqualTo("mygroupName");
    assertThat(dataCollectionInfo.getMetricCollectionInfoList()).isNotEmpty();
    assertThat(dataCollectionInfo.getDataCollectionDsl()).isEqualTo("metric-pack-dsl");

    List<MetricCollectionInfo> metricCollectionInfoList = dataCollectionInfo.getMetricCollectionInfoList();
    metricCollectionInfoList.forEach(metricCollectionInfo -> {
      assertThat(metricCollectionInfo.getMetricName()).isEqualTo("myMetric");
      assertThat(metricCollectionInfo.getMetricIdentifier()).isEqualTo("metricIdentifier");
      assertThat(metricCollectionInfo.getQuery())
          .isEqualTo("cpu_usage_total{app=\"cv-demo-app\",namespace=\"cv-demo\",filter2=\"cv-2\",filter3=\"cv-3\"}");
      assertThat(metricCollectionInfo.getFilters())
          .isEqualTo("app=\"cv-demo-app\",namespace=\"cv-demo\",filter2=\"cv-2\",filter3=\"cv-3\"");
    });
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo_WithServiceInstances() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .identifier("metricIdentifier")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .query("avg(metric_name({}))")
                                                   .serviceInstanceFieldName("pod_name")
                                                   .isManualQuery(true)
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo =
        mapper.toDeploymentDataCollectionInfo(cvConfig, Arrays.asList("n1", "n2"));

    List<MetricCollectionInfo> metricCollectionInfoList = dataCollectionInfo.getMetricCollectionInfoList();
    metricCollectionInfoList.forEach(metricCollectionInfo -> {
      assertThat(metricCollectionInfo.getQuery()).isEqualTo("avg(metric_name({pod_name=~\"n1|n2\"}))");
      assertThat(metricCollectionInfo.getFilters()).isEqualTo("pod_name=~\"n1|n2\"");
    });
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testToDataConnectionInfo_WithServiceInstancesAndFilters() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .identifier("metricIdentifier")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .query("avg(metric_name({service=\"cvng\"}))")
                                                   .serviceInstanceFieldName("pod_name")
                                                   .isManualQuery(true)
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo =
        mapper.toDeploymentDataCollectionInfo(cvConfig, Arrays.asList("n1", "n2"));

    List<MetricCollectionInfo> metricCollectionInfoList = dataCollectionInfo.getMetricCollectionInfoList();
    metricCollectionInfoList.forEach(metricCollectionInfo -> {
      assertThat(metricCollectionInfo.getQuery()).isEqualTo("avg(metric_name({service=\"cvng\",pod_name=~\"n1|n2\"}))");
      assertThat(metricCollectionInfo.getFilters()).isEqualTo("service=\"cvng\",pod_name=~\"n1|n2\"");
    });
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLI() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();
    ServiceLevelIndicator serviceLevelIndicator =
        ThresholdServiceLevelIndicator.builder().metric1("metricIdentifier").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .identifier("metricIdentifier")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .prometheusMetricName("cpu_usage_total")
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo =
        mapper.toDataCollectionInfo(Collections.singletonList(cvConfig), serviceLevelIndicator);
    assertThat(dataCollectionInfo.getMetricCollectionInfoList().size()).isEqualTo(1);
    assertThat(dataCollectionInfo.getMetricCollectionInfoList().get(0).getMetricIdentifier())
        .isEqualTo("metricIdentifier");
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForSLIWithDifferentMetricName() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();
    ServiceLevelIndicator serviceLevelIndicator =
        ThresholdServiceLevelIndicator.builder().metric1("metricIdentifier2").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo = PrometheusCVConfig.MetricInfo.builder()
                                                   .metricName("myMetric")
                                                   .identifier("metricIdentifier")
                                                   .metricType(TimeSeriesMetricType.RESP_TIME)
                                                   .prometheusMetricName("cpu_usage_total")
                                                   .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo =
        mapper.toDataCollectionInfo(Collections.singletonList(cvConfig), serviceLevelIndicator);
    assertThat(dataCollectionInfo.getMetricCollectionInfoList().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testToDataCollectionInfoForFiltersInCaseOfManualQuery() {
    MetricPack metricPack = MetricPack.builder().dataCollectionDsl("metric-pack-dsl").build();
    PrometheusCVConfig cvConfig = builderFactory.prometheusCVConfigBuilder().groupName("mygroupName").build();
    ServiceLevelIndicator serviceLevelIndicator =
        ThresholdServiceLevelIndicator.builder().metric1("metricIdentifier2").build();

    cvConfig.setMetricPack(metricPack);

    PrometheusCVConfig.MetricInfo metricInfo =
        PrometheusCVConfig.MetricInfo.builder()
            .metricName("myMetric")
            .identifier("metricIdentifier")
            .metricType(TimeSeriesMetricType.RESP_TIME)
            .prometheusMetricName("cpu_usage_total")
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(true).build())
            .isManualQuery(true)
            .query(
                "100 * sum(rate(istio_requests_total{destination_service_namespace=\"dev1-harness\",app=\"loginservice\",response_code=~\"[45]..\"}[5m]))/sum(rate(istio_requests_total{destination_service_namespace=\"dev1-harness\",app=\"loginservice\"}[5m]))")
            .build();

    cvConfig.setMetricInfoList(Arrays.asList(metricInfo));
    PrometheusDataCollectionInfo dataCollectionInfo = mapper.toDataCollectionInfo(cvConfig, TaskType.DEPLOYMENT);
    assertThat(dataCollectionInfo.getDslEnvVariables(PrometheusConnectorDTO.builder().build()).get("filterList"))
        .isEqualTo(Arrays.asList(
            "destination_service_namespace=\"dev1-harness\",app=\"loginservice\",response_code=~\"[45]..\""));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetHeaders() {
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder()
            .url("http://35.214.81.102:9090/")
            .username("test")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .headers(Collections.singletonList(CustomHealthKeyAndValue.builder().key("key").value("value").build()))
            .build();
    assertThat(PrometheusUtils.getHeaders(prometheusConnectorDTO))
        .isEqualTo(Maps.of("Authorization", "Basic dGVzdDpwYXNzd29yZA==", "key", "value"));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testDSLVersioningWithFeatureFlag() throws IllegalAccessException, IOException {
    featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(eq(builderFactory.getContext().getAccountId()),
             eq(FeatureName.SRM_ENABLE_AGGREGATION_USING_BY_IN_PROMETHEUS.name())))
        .thenReturn(true);
    FieldUtils.writeField(mapper, "featureFlagService", featureFlagService, true);
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    String query = "sum(avg(prometheus_http_requests_total{}) by (code))";
    String serviceInstanceFieldName = "handler";
    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .groupName("my_txn")
            .metricInfoList(Collections.singletonList(PrometheusCVConfig.MetricInfo.builder()
                                                          .query(query)
                                                          .metricType(TimeSeriesMetricType.RESP_TIME)
                                                          .identifier("createpayment")
                                                          .metricName("createpayment")
                                                          .serviceInstanceFieldName(serviceInstanceFieldName)
                                                          .isManualQuery(true)
                                                          .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        mapper.toDataCollectionInfo(prometheusCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    prometheusDataCollectionInfo.setCollectHostData(true);
    mapper.postProcessDataCollectionInfo(
        prometheusDataCollectionInfo, prometheusCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    assertThat(prometheusDataCollectionInfo.getDataCollectionDsl())
        .isEqualTo(readDSL("prometheus-v2-dsl-metric.datacollection"));
    assertThat(prometheusDataCollectionInfo.getMetricCollectionInfoList().get(0).getQuery())
        .isEqualTo(PrometheusQueryUtils.formGroupByQuery(query, serviceInstanceFieldName));
    when(featureFlagService.isFeatureFlagEnabled(eq(builderFactory.getContext().getAccountId()),
             eq(FeatureName.SRM_ENABLE_AGGREGATION_USING_BY_IN_PROMETHEUS.name())))
        .thenReturn(false);
    prometheusDataCollectionInfo =
        mapper.toDataCollectionInfo(prometheusCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    assertThat(prometheusDataCollectionInfo.getMetricCollectionInfoList().get(0).getQuery()).isEqualTo(query);
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(PrometheusCVConfig.class, "/io/harness/cvng/core/entities/" + name)),
        StandardCharsets.UTF_8);
  }
}
