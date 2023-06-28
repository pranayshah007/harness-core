/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.PrometheusDataCollectionInfo;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.beans.PrometheusMetricDefinition;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.PrometheusDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusDataCollectionDSLV2Test extends HoverflyCVNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;

  @Inject private PrometheusDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;

  FeatureFlagService featureFlagService;

  @Before
  public void setup() throws IOException, IllegalAccessException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    featureFlagService = mock(FeatureFlagService.class);
    when(featureFlagService.isFeatureFlagEnabled(eq(builderFactory.getContext().getAccountId()),
             eq(FeatureName.SRM_ENABLE_AGGREGATION_USING_BY_IN_PROMETHEUS.name())))
        .thenReturn(true);
    FieldUtils.writeField(dataCollectionInfoMapper, "featureFlagService", featureFlagService, true);
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_prometheusDSLWithHostData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("prometheus-v2-dsl-metric.datacollection");
    Instant instant = Instant.parse("2023-06-15T10:21:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .groupName("my_txn")
            .metricInfoList(Collections.singletonList(PrometheusCVConfig.MetricInfo.builder()
                                                          .query("sum(avg(prometheus_http_requests_total{}) by (code))")
                                                          .metricType(TimeSeriesMetricType.RESP_TIME)
                                                          .identifier("createpayment")
                                                          .metricName("createpayment")
                                                          .serviceInstanceFieldName("handler")
                                                          .isManualQuery(true)
                                                          .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, VerificationTask.TaskType.DEPLOYMENT);

    prometheusDataCollectionInfo.setCollectHostData(true);
    dataCollectionInfoMapper.postProcessDataCollectionInfo(
        prometheusDataCollectionInfo, prometheusCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder()
            .url("https://prometheus.demo.do.prometheus.io/")
            .username("test")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .build();
    Map<String, Object> params = prometheusDataCollectionInfo.getDslEnvVariables(prometheusConnectorDTO);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(2)))
            .endTime(instant)
            .commonHeaders(prometheusDataCollectionInfo.collectionHeaders(prometheusConnectorDTO))
            .otherEnvVariables(params)
            .baseUrl("https://prometheus.demo.do.prometheus.io/")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, System.out::println);
    assertThat(prometheusDataCollectionInfo.collectionHeaders(prometheusConnectorDTO))
        .isEqualTo(Collections.singletonMap("Authorization", "Basic dGVzdDpwYXNzd29yZA=="));
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("expected-prometheus-dsl-v2-output.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testExecute_prometheusDSL_SLI() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("prometheus-v2-dsl-metric.datacollection");
    Instant endInstant = Instant.parse("2023-06-15T10:21:00.000Z");
    Instant startInstant = endInstant.minus(Duration.ofMinutes(5));
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.PROMETHEUS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .groupName("grp_test")
            .metricInfoList(Collections.singletonList(
                PrometheusCVConfig.MetricInfo.builder()
                    .metricName("prometheus_http_requests_total")
                    .identifier("prometheus_http_requests_total")
                    .prometheusMetricName("prometheus_http_requests_total")
                    .serviceFilter(Collections.singletonList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                                 .labelName("code")
                                                                 .labelValue("200")
                                                                 .build()))
                    .envFilter(Collections.singletonList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                             .labelName("job")
                                                             .labelValue("prometheus")
                                                             .build()))
                    .aggregation("avg")
                    .serviceInstanceFieldName("handler")
                    .isManualQuery(false)
                    .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    prometheusCVConfig.getMetricPack().setDataCollectionDsl(readOldDSL("metric-collection.datacollection"));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, VerificationTask.TaskType.SLI);
    prometheusDataCollectionInfo.setCollectHostData(false);
    prometheusDataCollectionInfo.setGroupName("grp_test");
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder()
            .url("https://prometheus.demo.do.prometheus.io/")
            .username("test")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .build();
    Map<String, Object> params = prometheusDataCollectionInfo.getDslEnvVariables(prometheusConnectorDTO);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(startInstant)
            .endTime(endInstant)
            .commonHeaders(prometheusDataCollectionInfo.collectionHeaders(prometheusConnectorDTO))
            .otherEnvVariables(params)
            .baseUrl("https://prometheus.demo.do.prometheus.io/")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, System.out::println);
    System.out.println();

    assertThat(timeSeriesRecords).hasSize(6);
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1686824160000L);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("grp_test");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(27443.59523809524);
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(PrometheusCVConfig.class, "/io/harness/cvng/core/entities/" + name)),
        StandardCharsets.UTF_8);
  }

  private String readOldDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/prometheus/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/prometheus/" + name)), StandardCharsets.UTF_8);
  }
}
