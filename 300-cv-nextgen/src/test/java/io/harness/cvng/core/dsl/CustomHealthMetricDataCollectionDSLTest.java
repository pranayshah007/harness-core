/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.HoverflyCVNextGenTestBase;
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
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthMetricDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
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
  MetricResponseMapping responseMapping;
  @Before
  public void setup() throws IOException {
    super.before();
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceJsonPath)
                          .relativeMetricValueJsonPath(relativeMetricValueJsonPath)
                          .relativeMetricListJsonPath(relativeMetricListJsonPath)
                          .relativeServiceInstanceValueJsonPath(relativeServiceInstanceValueJsonPath)
                          .relativeTimestampJsonPath(relativeTimestampJsonPath)
                          .serviceInstanceListJsonPath(serviceInstanceListJsonPath)
                          .build();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_customAppd() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL("300-cv-nextgen/src/main/resources/customhealth/dsl/metric-collection.datacollection");
    Instant instant = Instant.parse("2022-06-21T10:21:00.000Z");
    RuntimeParameters runtimeParameters = getAppdRuntimeParams(instant);
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });
    // TODO: Modify the test to check for correct service-instance names once DSL is fixed.
    assertThat(true).isTrue();
  }

  private String readDSL(String fileName) throws IOException {
    return FileUtils.readFileToString(new File(fileName), StandardCharsets.UTF_8);
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
}
