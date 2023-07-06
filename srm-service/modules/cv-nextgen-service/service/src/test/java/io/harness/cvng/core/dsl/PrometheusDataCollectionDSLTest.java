/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.PrometheusDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.CallDetails;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private PrometheusDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;

  @Before
  public void setup() throws IOException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testExecute_prometheusDSLWithHostData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-06-01T10:21:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(PrometheusCVConfig.MetricInfo.builder()
                                                          .query("avg(\n"
                                                              + "\tgauge_servo_response_mvc_createpayment\t{\n"
                                                              + "\n"
                                                              + "\t\tjob=\"payment-service-nikpapag\"\n"
                                                              + "\n"
                                                              + "})")
                                                          .metricType(TimeSeriesMetricType.RESP_TIME)
                                                          .identifier("createpayment")
                                                          .metricName("createpayment")
                                                          .serviceInstanceFieldName("pod")
                                                          .isManualQuery(true)
                                                          .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, TaskType.DEPLOYMENT);
    prometheusDataCollectionInfo.setCollectHostData(true);
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder()
            .url("http://35.214.81.102:9090/")
            .username("test")
            .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
            .build();
    Map<String, Object> params = prometheusDataCollectionInfo.getDslEnvVariables(prometheusConnectorDTO);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(5)))
            .endTime(instant)
            .commonHeaders(prometheusDataCollectionInfo.collectionHeaders(prometheusConnectorDTO))
            .otherEnvVariables(params)
            .baseUrl("http://35.214.81.102:9090/")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(prometheusDataCollectionInfo.collectionHeaders(prometheusConnectorDTO))
        .isEqualTo(Collections.singletonMap("Authorization", "Basic dGVzdDpwYXNzd29yZA=="));
    assertThat(Sets.newHashSet(timeSeriesRecords))
        .isEqualTo(new Gson().fromJson(
            readJson("expected-prometheus-dsl-output.json"), new TypeToken<Set<TimeSeriesRecord>>() {}.getType()));
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  // Host filter works only with newer version of Prometheus, hence working with local prometheus setup for now
  public void testExecute_prometheusDSLWithHostDataHostFilter() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-06-12T20:34:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(PrometheusCVConfig.MetricInfo.builder()
                                                          .query("avg(\n"
                                                              + "\tlearning_engine_task_non_final_status_count\t{\n"
                                                              + "\n"
                                                              + "\t\taccountId=\"kmpySmUISimoRrJL6NL73w\"\n"
                                                              + "\n"
                                                              + "})")
                                                          .metricType(TimeSeriesMetricType.RESP_TIME)
                                                          .identifier("createpayment")
                                                          .metricName("createpayment")
                                                          .serviceInstanceFieldName("instance")
                                                          .isManualQuery(true)
                                                          .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, TaskType.DEPLOYMENT);
    prometheusDataCollectionInfo.setCollectHostData(true);
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder().url("http://prometheus.local:9090/").build();
    Map<String, Object> params = prometheusDataCollectionInfo.getDslEnvVariables(prometheusConnectorDTO);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofDays(1)))
            .endTime(instant)
            .commonHeaders(prometheusDataCollectionInfo.collectionHeaders(prometheusConnectorDTO))
            .otherEnvVariables(params)
            .baseUrl("http://prometheus.local:9090/")
            .build();
    List<CallDetails> callsMade = new ArrayList<>();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { callsMade.add(callDetails); });
    CallDetails labelsCallForHost = callsMade.stream()
                                        .filter(call -> call.getRequest().request().url().toString().contains("values"))
                                        .findAny()
                                        .get();
    assertThat(labelsCallForHost.getResponse().body().toString())
        .isEqualTo("{status=success, data=[host.docker.internal:8889]}");
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @Ignore("Ignore till delegate is deployed with new Data collection Info and DSL Changes")
  public void testExecute_prometheusDSLWithTooLargeHostData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-05-27T10:21:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.APP_DYNAMICS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(PrometheusCVConfig.MetricInfo.builder()
                                                          .query("avg(\n"
                                                              + "\tgauge_servo_response_mvc_createpayment\t{\n"
                                                              + "\n"
                                                              + "\t\tjob=\"payment-service-nikpapag\"\n"
                                                              + "\n"
                                                              + "})")
                                                          .metricType(TimeSeriesMetricType.RESP_TIME)
                                                          .identifier("createpayment")
                                                          .metricName("createpayment")
                                                          .serviceInstanceFieldName("pod")
                                                          .isManualQuery(true)
                                                          .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, TaskType.DEPLOYMENT);
    prometheusDataCollectionInfo.setCollectHostData(true);
    prometheusDataCollectionInfo.setMaximumHostSizeAllowed(1);

    Map<String, Object> params = prometheusDataCollectionInfo.getDslEnvVariables(
        PrometheusConnectorDTO.builder().url("http://35.214.81.102:9090/").build());

    Map<String, String> headers = new HashMap<>();
    RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                              .startTime(instant.minus(Duration.ofMinutes(5)))
                                              .endTime(instant)
                                              .commonHeaders(headers)
                                              .otherEnvVariables(params)
                                              .baseUrl("http://35.214.81.102:9090/")
                                              .build();
    assertThatThrownBy(()
                           -> dataCollectionDSLService.execute(
                               code, runtimeParameters, callDetails -> { System.out.println(callDetails); }))
        .hasMessage(
            "Host list returned from Prometheus is of size 2, which is greater than allowed 1, please check the query");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_prometheusDSL_SLI() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant endInstant = Instant.parse("2022-07-25T19:53:28.000Z");
    Instant startInstant = endInstant.minus(Duration.ofMinutes(5));
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.PROMETHEUS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(
                PrometheusCVConfig.MetricInfo.builder()
                    .metricName("container_file_descriptors")
                    .identifier("container_file_descriptors")
                    .prometheusMetricName("container_file_descriptors")
                    .envFilter(Collections.singletonList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                             .labelName("job")
                                                             .labelValue("kubernetes-cadvisor")
                                                             .build()))
                    .serviceFilter(Collections.singletonList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                                 .labelName("namespace")
                                                                 .labelValue("cv-demo")
                                                                 .build()))
                    .isManualQuery(false)
                    .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, TaskType.SLI);
    prometheusDataCollectionInfo.setCollectHostData(false);
    prometheusDataCollectionInfo.setGroupName("g1");
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder()
            .url("http://35.226.185.156:8080/")
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
            .baseUrl("http://35.226.185.156:8080/")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(timeSeriesRecords).hasSize(90);
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1658778508000L);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(3.0);
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isGreaterThanOrEqualTo(startInstant.toEpochMilli());
    assertThat(timeSeriesRecords.get(89).getTimestamp()).isLessThanOrEqualTo(endInstant.toEpochMilli());
    assertThat(timeSeriesRecords.get(89).getTimestamp()).isEqualTo(1658778808000L);
    assertThat(timeSeriesRecords.get(89).getMetricValue()).isEqualTo(0.0);
    assertThat(timeSeriesRecords.get(89).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("container_file_descriptors");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("container_file_descriptors");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_prometheusDSL_forLiveMonitoring() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant endInstant = Instant.parse("2022-07-25T19:53:28.000Z");
    Instant startInstant = endInstant.minus(Duration.ofMinutes(5));
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.PROMETHEUS);

    PrometheusCVConfig prometheusCVConfig =
        builderFactory.prometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(
                PrometheusCVConfig.MetricInfo.builder()
                    .metricName("container_file_descriptors")
                    .identifier("container_file_descriptors")
                    .prometheusMetricName("container_file_descriptors")
                    .envFilter(Collections.singletonList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                             .labelName("job")
                                                             .labelValue("kubernetes-cadvisor")
                                                             .build()))
                    .serviceFilter(Collections.singletonList(PrometheusMetricDefinition.PrometheusFilter.builder()
                                                                 .labelName("namespace")
                                                                 .labelValue("cv-demo")
                                                                 .build()))
                    .isManualQuery(false)
                    .metricType(TimeSeriesMetricType.INFRA)
                    .build()))
            .build();
    prometheusCVConfig.setMetricPack(metricPacks.get(0));
    PrometheusDataCollectionInfo prometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(prometheusCVConfig, TaskType.LIVE_MONITORING);
    prometheusDataCollectionInfo.setCollectHostData(false);
    prometheusDataCollectionInfo.setGroupName("g1");
    PrometheusConnectorDTO prometheusConnectorDTO =
        PrometheusConnectorDTO.builder()
            .url("http://35.226.185.156:8080/")
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
            .baseUrl("http://35.226.185.156:8080/")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords =
        (List<TimeSeriesRecord>) dataCollectionDSLService.execute(code, runtimeParameters, System.out::println);
    assertThat(timeSeriesRecords).hasSize(90);
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isEqualTo(1658778508000L);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getMetricValue()).isEqualTo(3.0);
    assertThat(timeSeriesRecords.get(0).getTimestamp()).isGreaterThanOrEqualTo(startInstant.toEpochMilli());
    assertThat(timeSeriesRecords.get(89).getTimestamp()).isLessThanOrEqualTo(endInstant.toEpochMilli());
    assertThat(timeSeriesRecords.get(89).getTimestamp()).isEqualTo(1658778808000L);
    assertThat(timeSeriesRecords.get(89).getMetricValue()).isEqualTo(0.0);
    assertThat(timeSeriesRecords.get(89).getTxnName()).isEqualTo("g1");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("container_file_descriptors");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("container_file_descriptors");
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/prometheus/dsl/" + name)), StandardCharsets.UTF_8);
  }

  private String readJson(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getResourceFilePath("hoverfly/prometheus/" + name)), StandardCharsets.UTF_8);
  }
}
