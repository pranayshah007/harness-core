/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.ARPITJ;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.CustomHealthDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.CustomHealthMetricDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthMetricDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  @Inject private MetricPackService metricPackService;
  @Inject private CustomHealthMetricDataCollectionInfoMapper dataCollectionInfoMapper;

  private ExecutorService executorService;
  private DataCollectionDSLService dataCollectionDSLService;
  private String code;
  BuilderFactory builderFactory;
  String metricValueJSONPath = "$.[*].metricValues.[*].value";
  String serviceInstanceJsonPath = "$.[*].metricPath";
  String timestampValueJSONPath = "$.[*].metricValues.[*].startTimeInMillis";
  String serviceInstanceListJsonPath = "$";
  String relativeMetricListJsonPath = "metricValues";
  String relativeTimestampJsonPath = "startTimeInMillis";
  String relativeMetricValueJsonPath = "value";
  String relativeServiceInstanceValueJsonPath = "metricPath";

  String timestampFormat = null;
  MetricResponseMapping responseMapping;
  @Before
  public void setup() throws IOException {
    super.before();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_customAppd() throws IOException {
    populateAppdPaths();
    populateResponseMapping();
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL(
        "srm-service/modules/cv-nextgen-service/service/src/main/resources/customhealth/dsl/metric-collection.datacollection");
    Instant instant = Instant.parse("2022-06-21T10:21:00.000Z");
    RuntimeParameters runtimeParameters = getAppdRuntimeParams(instant);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    Map<String, List<TimeSeriesRecord>> resultMap =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(resultMap.keySet().size()).isEqualTo(3);
    assertThat(resultMap.get("Overall Application Performance|docker-tier|Individual Nodes|cdng--246|Errors per Minute")
                   .size())
        .isEqualTo(2);
    assertThat(resultMap.get("Overall Application Performance|docker-tier|Individual Nodes|cdng--244|Errors per Minute")
                   .size())
        .isEqualTo(11);
    assertThat(resultMap.get("Overall Application Performance|docker-tier|Individual Nodes|cdng--245|Errors per Minute")
                   .size())
        .isEqualTo(10);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_customELK() throws IOException {
    populateELKPaths();
    populateResponseMapping();
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL(
        "srm-service/modules/cv-nextgen-service/service/src/main/resources/customhealth/dsl/metric-collection.datacollection");
    Instant instant = Instant.parse("2022-10-12T03:55:00.000Z");
    String index = "arpit3";
    String requestBody = "{\n"
        + "    \"from\" : 0, \n"
        + "    \"size\" : 100,\n"
        + "    \"query\" : {\n"
        + "    \"bool\": {\n"
        + "      \"filter\": [\n"
        + "        {\n"
        + "          \"query_string\":{\n"
        + "              \"query\": \"*\"\n"
        + "          }\n"
        + "        },\n"
        + "        {\n"
        + "            \"range\":{\n"
        + "                \"metricValues.startTimeInMillis\":{\n"
        + "                    \"gt\":start_time,\n"
        + "                    \"lte\":end_time\n"
        + "                }\n"
        + "            }\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  }\n"
        + "}";
    RuntimeParameters runtimeParameters = getELKRuntimeParams(instant, requestBody, index);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    Map<String, List<TimeSeriesRecord>> resultMap =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(resultMap.keySet().size()).isNotZero();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_customELKForServiceHealth() throws IOException {
    populateELKPaths();
    serviceInstanceJsonPath = null;
    relativeServiceInstanceValueJsonPath = null;
    populateResponseMapping();
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL(
        "srm-service/modules/cv-nextgen-service/service/src/main/resources/customhealth/dsl/metric-collection.datacollection");
    Instant instant = Instant.parse("2022-10-12T03:55:00.000Z");
    String index = "arpit3";
    String requestBody = "{\n"
        + "    \"from\" : 0, \n"
        + "    \"size\" : 100,\n"
        + "    \"query\" : {\n"
        + "    \"bool\": {\n"
        + "      \"filter\": [\n"
        + "        {\n"
        + "          \"query_string\":{\n"
        + "              \"query\": \"*\"\n"
        + "          }\n"
        + "        },\n"
        + "        {\n"
        + "            \"range\":{\n"
        + "                \"metricValues.startTimeInMillis\":{\n"
        + "                    \"gt\":start_time,\n"
        + "                    \"lte\":end_time\n"
        + "                }\n"
        + "            }\n"
        + "        }\n"
        + "      ]\n"
        + "    }\n"
        + "  }\n"
        + "}";
    RuntimeParameters runtimeParameters = getELKRuntimeParams(instant, requestBody, index);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    assertThat(timeSeriesRecords.size()).isNotZero();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testExecute_customELKWithAggregation() throws IOException {
    populateELKPathsForAggregation();
    populateResponseMapping();
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL(
        "srm-service/modules/cv-nextgen-service/service/src/main/resources/customhealth/dsl/metric-collection.datacollection");
    Instant instant = Instant.parse("2022-10-31T09:27:00.000Z");
    String index = "integration-test-1";
    String requestBody = "{\n"
        + "    \"from\": 0,\n"
        + "    \"size\": 100,\n"
        + "    \"query\": {\n"
        + "        \"bool\": {\n"
        + "            \"must\": [\n"
        + "                {\n"
        + "                    \"query_string\": {\n"
        + "                        \"query\": \"*\"\n"
        + "                    }\n"
        + "                },\n"
        + "                {\n"
        + "                    \"range\": {\n"
        + "                        \"@timestamp\": {\n"
        + "                            \"gt\":start_time,\n"
        + "                            \"lte\":end_time\n"
        + "                        }\n"
        + "                    }\n"
        + "                }\n"
        + "            ]\n"
        + "        }\n"
        + "    },\n"
        + "  \"aggs\":{\n"
        + "    \"by_district\":{\n"
        + "      \"terms\": {\n"
        + "        \"field\": \"hostname\"\n"
        + "      },\n"
        + "      \"aggs\": {\n"
        + "        \"tops\": {\n"
        + "          \"top_hits\": {\n"
        + "            \"size\": 10\n"
        + "          }\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}";
    RuntimeParameters runtimeParameters = getELKRuntimeParamsForAggregation(instant, requestBody, index);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    Map<String, List<TimeSeriesRecord>> resultMap =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(resultMap.keySet().size()).isNotZero();
  }

  private String readDSL(String fileName) throws IOException {
    return FileUtils.readFileToString(new File(fileName), StandardCharsets.UTF_8);
  }

  private void populateResponseMapping() {
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceJsonPath)
                          .relativeMetricValueJsonPath(relativeMetricValueJsonPath)
                          .relativeMetricListJsonPath(relativeMetricListJsonPath)
                          .relativeServiceInstanceValueJsonPath(relativeServiceInstanceValueJsonPath)
                          .relativeTimestampJsonPath(relativeTimestampJsonPath)
                          .serviceInstanceListJsonPath(serviceInstanceListJsonPath)
                          .timestampFormat(timestampFormat)
                          .build();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  private RuntimeParameters getAppdRuntimeParams(Instant instant) {
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.CUSTOM_HEALTH_METRIC);
    metricPacks.forEach(
        metricPack -> metricPackService.populateDataCollectionDsl(DataSourceType.CUSTOM_HEALTH_METRIC, metricPack));
    CustomHealthMetricCVConfig customHealthMetricCVConfig = builderFactory.customHealthMetricCVConfigBuilderForAppd(
        "CustomHealth Metric", true, false, true, responseMapping, "g1", HealthSourceQueryType.HOST_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);
    customHealthMetricCVConfig.setMetricPack(metricPacks.get(0));
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(customHealthMetricCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    customHealthDataCollectionInfo.setCollectHostData(true);
    CustomHealthConnectorDTO customHealthConnectorDTO =
        CustomHealthConnectorDTO.builder()
            .baseURL("https://harness-test.saas.appdynamics.com/controller/")
            .method(CustomHealthMethod.GET)
            .headers(new ArrayList<>())
            .params(new ArrayList<>())
            .validationPath("rest/applications?output=json")
            .build();
    customHealthConnectorDTO.getHeaders().add(
        CustomHealthKeyAndValue.builder().key("Authorization").isValueEncrypted(false).value("Basic **").build());

    Map<String, Object> params = customHealthDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO);
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic **");
    // Replace this with the actual value
    // when capturing the request.
    return RuntimeParameters.builder()
        .startTime(instant.minusSeconds(7200))
        .endTime(instant)
        .commonHeaders(headers)
        .otherEnvVariables(params)
        .baseUrl("https://harness-test.saas.appdynamics.com/controller/")
        .build();
  }

  private RuntimeParameters getELKRuntimeParams(Instant instant, String requestBody, String index) {
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.CUSTOM_HEALTH_METRIC);
    metricPacks.forEach(
        metricPack -> metricPackService.populateDataCollectionDsl(DataSourceType.CUSTOM_HEALTH_METRIC, metricPack));
    CustomHealthMetricCVConfig customHealthMetricCVConfig = builderFactory.customHealthMetricCVConfigBuilderForELK(
        "CustomHealth Metric", true, false, true, responseMapping, "g1", HealthSourceQueryType.HOST_BASED,
        CustomHealthMethod.POST, CVMonitoringCategory.PERFORMANCE, requestBody, index);
    customHealthMetricCVConfig.setMetricPack(metricPacks.get(0));
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(customHealthMetricCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    customHealthDataCollectionInfo.setCollectHostData(true);
    CustomHealthConnectorDTO customHealthConnectorDTO =
        CustomHealthConnectorDTO.builder()
            .baseURL("https://arpitelkcloud.es.us-central1.gcp.cloud.es.io/")
            .method(CustomHealthMethod.GET)
            .headers(new ArrayList<>())
            .params(new ArrayList<>())
            .validationPath("*/_search")
            .build();
    customHealthConnectorDTO.getHeaders().add(CustomHealthKeyAndValue.builder()
                                                  .key("Authorization")
                                                  .isValueEncrypted(false)
                                                  .value("Basic ZWxhc3RpYzpFR1JoRXg5SFZHN2ZvQVB6TFpQM3lielY=")
                                                  .build());
    customHealthConnectorDTO.getHeaders().add(CustomHealthKeyAndValue.builder()
                                                  .key("Content-Type")
                                                  .isValueEncrypted(false)
                                                  .value("application/json")
                                                  .build());

    Map<String, Object> params = customHealthDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO);
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Basic ZWxhc3RpYzpFR1JoRXg5SFZHN2ZvQVB6TFpQM3lielY=");
    // Replace this with the actual value
    // when capturing the request.
    return RuntimeParameters.builder()
        .startTime(instant.minusSeconds(7200))
        .endTime(instant)
        .commonHeaders(headers)
        .otherEnvVariables(params)
        .baseUrl("https://arpitelkcloud.es.us-central1.gcp.cloud.es.io/")
        .build();
  }

  private RuntimeParameters getELKRuntimeParamsForAggregation(Instant instant, String requestBody, String index) {
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.CUSTOM_HEALTH_METRIC);
    metricPacks.forEach(
        metricPack -> metricPackService.populateDataCollectionDsl(DataSourceType.CUSTOM_HEALTH_METRIC, metricPack));
    CustomHealthMetricCVConfig customHealthMetricCVConfig = builderFactory.customHealthMetricCVConfigBuilderForELK(
        "CustomHealth Metric", true, false, true, responseMapping, "g1", HealthSourceQueryType.HOST_BASED,
        CustomHealthMethod.POST, CVMonitoringCategory.PERFORMANCE, requestBody, index);
    customHealthMetricCVConfig.setMetricPack(metricPacks.get(0));
    CustomHealthDataCollectionInfo customHealthDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(customHealthMetricCVConfig, VerificationTask.TaskType.DEPLOYMENT);
    customHealthDataCollectionInfo.setCollectHostData(true);
    CustomHealthConnectorDTO customHealthConnectorDTO = CustomHealthConnectorDTO.builder()
                                                            .baseURL("http://elk6.dev.harness.io:9200/")
                                                            .method(CustomHealthMethod.GET)
                                                            .headers(new ArrayList<>())
                                                            .params(new ArrayList<>())
                                                            .validationPath("*/_search")
                                                            .build();
    customHealthConnectorDTO.getHeaders().add(CustomHealthKeyAndValue.builder()
                                                  .key("Content-Type")
                                                  .isValueEncrypted(false)
                                                  .value("application/json")
                                                  .build());

    Map<String, Object> params = customHealthDataCollectionInfo.getDslEnvVariables(customHealthConnectorDTO);
    Map<String, String> headers = new HashMap<>();
    // headers.put("Authorization", "Basic ZWxhc3RpYzpFR1JoRXg5SFZHN2ZvQVB6TFpQM3lielY=");
    //  Replace this with the actual value
    //  when capturing the request.
    return RuntimeParameters.builder()
        .startTime(instant.minusSeconds(7200))
        .endTime(instant)
        .commonHeaders(headers)
        .otherEnvVariables(params)
        .baseUrl("http://elk6.dev.harness.io:9200/")
        .build();
  }

  private void populateAppdPaths() {
    metricValueJSONPath = "$.[*].metricValues.[*].value";
    serviceInstanceJsonPath = "$.[*].metricPath";
    timestampValueJSONPath = "$.[*].metricValues.[*].startTimeInMillis";
    serviceInstanceListJsonPath = "$";
    relativeMetricListJsonPath = "metricValues";
    relativeTimestampJsonPath = "startTimeInMillis";
    relativeMetricValueJsonPath = "value";
    relativeServiceInstanceValueJsonPath = "metricPath";
  }

  private void populateELKPaths() {
    metricValueJSONPath = "$.hits.hits.[*]._source.metricValues.[*].value";
    serviceInstanceJsonPath = "$.hits.hits.[*]._source.metricPath";
    timestampValueJSONPath = "$.hits.hits.[*]._source.metricValues.[*].startTimeInMillis";
    serviceInstanceListJsonPath = "$.hits.hits";
    relativeMetricListJsonPath = "_source.metricValues";
    relativeTimestampJsonPath = "startTimeInMillis";
    relativeMetricValueJsonPath = "value";
    relativeServiceInstanceValueJsonPath = "_source.metricPath";
  }

  private void populateELKPathsForAggregation() {
    metricValueJSONPath = "$.aggregations.by_district.buckets.[*].tops.hits.hits.[*]._score";
    serviceInstanceJsonPath = "$.aggregations.by_district.buckets.[*].key";
    timestampValueJSONPath = "$.aggregations.by_district.buckets.[*].tops.hits.hits.[*]._source.@timestamp";
    serviceInstanceListJsonPath = "$.aggregations.by_district.buckets";
    relativeMetricListJsonPath = "tops.hits.hits";
    relativeTimestampJsonPath = "_source.@timestamp";
    relativeMetricValueJsonPath = "_score";
    relativeServiceInstanceValueJsonPath = "key";
    timestampFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  }
}
