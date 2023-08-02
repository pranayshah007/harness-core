/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.dsl;

import static io.harness.CvNextGenTestBase.getSourceResourceFile;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig.MetricInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.impl.AwsPrometheusDataCollectionInfoMapper;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.datacollection.entity.TimeSeriesRecord;
import io.harness.datacollection.impl.DataCollectionServiceImpl;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsPrometheusDataCollectionDSLTestSuiteChild extends DSLHoverflyCVNextGenTestSuiteChildBase {
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private AwsPrometheusDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;
  String accessKey;
  String secretKey;
  AwsConnectorDTO testAwsConnector;
  AwsPrometheusDataCollectionInfo awsPrometheusDataCollectionInfo;
  DataCollectionDSLService dataCollectionDSLService;
  String code;
  Instant instant;
  List<MetricPack> metricPacks;

  @Before
  public void setup() throws IOException {
    super.before();
    builderFactory = BuilderFactory.getDefault();
    executorService = Executors.newFixedThreadPool(10);
    metricPackService.createDefaultMetricPackAndThresholds(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier());
    accessKey = "***";
    secretKey = "***";
    testAwsConnector =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey(accessKey)
                                .secretKeyRef(SecretRefData.builder().decryptedValue(secretKey.toCharArray()).build())
                                .build())
                    .build())
            .build();
    dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    code = readDSL("metric-collection.datacollection");
    instant = Instant.parse("2022-10-31T11:00:00.000Z");
    metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.AWS_PROMETHEUS);

    AwsPrometheusCVConfig awsPrometheusCVConfig =
        (AwsPrometheusCVConfig) builderFactory.awsPrometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(
                MetricInfo.builder()
                    .query("avg(container_threads{alpha_eksctl_io_cluster_name=\"srm-eks-cluster-one\"})")
                    .metricType(TimeSeriesMetricType.RESP_TIME)
                    .identifier("metric1")
                    .metricName("Metric 1")
                    .serviceInstanceFieldName("instance")
                    .isManualQuery(true)
                    .build()))
            .groupName("group1")
            .build();
    awsPrometheusCVConfig.setMetricPack(metricPacks.get(0));
    awsPrometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(awsPrometheusCVConfig, TaskType.DEPLOYMENT);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_awsPrometheusDSL_withoutHostData() {
    awsPrometheusDataCollectionInfo.setCollectHostData(false);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(5)))
            .endTime(instant)
            .commonHeaders(awsPrometheusDataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(awsPrometheusDataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl("")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });

    assertThat(timeSeriesRecords.size()).isEqualTo(6);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("group1");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("Metric 1");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("metric1");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_awsPrometheusDSL_withHostData() {
    awsPrometheusDataCollectionInfo.setCollectHostData(true);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(5)))
            .endTime(instant)
            .commonHeaders(awsPrometheusDataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(awsPrometheusDataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl("")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> System.out.println(callDetails));

    assertThat(timeSeriesRecords.size()).isEqualTo(12);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("group1");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("Metric 1");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("metric1");

    Map<String, List<TimeSeriesRecord>> instanceMap =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(instanceMap.keySet().size()).isEqualTo(2);
    assertThat(instanceMap.get("ip-192-168-23-156.ec2.internal").size()).isEqualTo(6);
    assertThat(instanceMap.get("ip-192-168-59-131.ec2.internal").size()).isEqualTo(6);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_awsPrometheusDSL_withoutHostData_multipleMetrics() {
    List<MetricInfo> metricInfos = new ArrayList<>();
    metricInfos.add(MetricInfo.builder()
                        .query("avg(container_threads{alpha_eksctl_io_cluster_name=\"srm-eks-cluster-one\"})")
                        .metricType(TimeSeriesMetricType.RESP_TIME)
                        .identifier("metric1")
                        .metricName("Metric 1")
                        .serviceInstanceFieldName("instance")
                        .isManualQuery(true)
                        .build());
    metricInfos.add(MetricInfo.builder()
                        .query("avg(container_threads{alpha_eksctl_io_cluster_name=\"srm-eks-cluster-one\"})")
                        .metricType(TimeSeriesMetricType.RESP_TIME)
                        .identifier("metric2")
                        .metricName("Metric 2")
                        .serviceInstanceFieldName("instance")
                        .isManualQuery(true)
                        .build());
    AwsPrometheusCVConfig awsPrometheusCVConfig = (AwsPrometheusCVConfig) builderFactory.awsPrometheusCVConfigBuilder()
                                                      .metricInfoList(metricInfos)
                                                      .groupName("group1")
                                                      .build();
    awsPrometheusCVConfig.setMetricPack(metricPacks.get(0));
    awsPrometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(awsPrometheusCVConfig, TaskType.DEPLOYMENT);
    awsPrometheusDataCollectionInfo.setCollectHostData(false);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(5)))
            .endTime(instant)
            .commonHeaders(awsPrometheusDataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(awsPrometheusDataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl("")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });

    assertThat(timeSeriesRecords.size()).isEqualTo(12);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("group1");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("Metric 1");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("metric1");
    assertThat(timeSeriesRecords.get(0).getHostname()).isNull();
    assertThat(timeSeriesRecords.get(6).getMetricName()).isEqualTo("Metric 2");
    assertThat(timeSeriesRecords.get(6).getMetricIdentifier()).isEqualTo("metric2");
    assertThat(timeSeriesRecords.get(6).getHostname()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testExecute_awsPrometheusDSL_withHostData_multipleMetrics() {
    List<MetricInfo> metricInfos = new ArrayList<>();
    metricInfos.add(MetricInfo.builder()
                        .query("avg(container_threads{alpha_eksctl_io_cluster_name=\"srm-eks-cluster-one\"})")
                        .metricType(TimeSeriesMetricType.RESP_TIME)
                        .identifier("metric1")
                        .metricName("Metric 1")
                        .serviceInstanceFieldName("instance")
                        .isManualQuery(true)
                        .build());
    metricInfos.add(MetricInfo.builder()
                        .query("avg(container_threads{alpha_eksctl_io_cluster_name=\"srm-eks-cluster-one\"})")
                        .metricType(TimeSeriesMetricType.RESP_TIME)
                        .identifier("metric2")
                        .metricName("Metric 2")
                        .serviceInstanceFieldName("instance")
                        .isManualQuery(true)
                        .build());
    AwsPrometheusCVConfig awsPrometheusCVConfig = (AwsPrometheusCVConfig) builderFactory.awsPrometheusCVConfigBuilder()
                                                      .metricInfoList(metricInfos)
                                                      .groupName("group1")
                                                      .build();
    awsPrometheusCVConfig.setMetricPack(metricPacks.get(0));
    awsPrometheusDataCollectionInfo =
        dataCollectionInfoMapper.toDataCollectionInfo(awsPrometheusCVConfig, TaskType.DEPLOYMENT);
    awsPrometheusDataCollectionInfo.setCollectHostData(true);

    RuntimeParameters runtimeParameters =
        RuntimeParameters.builder()
            .startTime(instant.minus(Duration.ofMinutes(5)))
            .endTime(instant)
            .commonHeaders(awsPrometheusDataCollectionInfo.collectionHeaders(testAwsConnector))
            .otherEnvVariables(awsPrometheusDataCollectionInfo.getDslEnvVariables(testAwsConnector))
            .baseUrl("")
            .build();
    List<TimeSeriesRecord> timeSeriesRecords = (List<TimeSeriesRecord>) dataCollectionDSLService.execute(
        code, runtimeParameters, callDetails -> { System.out.println(callDetails); });

    assertThat(timeSeriesRecords.size()).isEqualTo(24);
    assertThat(timeSeriesRecords.get(0).getTxnName()).isEqualTo("group1");
    assertThat(timeSeriesRecords.get(0).getMetricName()).isEqualTo("Metric 1");
    assertThat(timeSeriesRecords.get(0).getMetricIdentifier()).isEqualTo("metric1");
    assertThat(timeSeriesRecords.get(12).getMetricName()).isEqualTo("Metric 2");
    assertThat(timeSeriesRecords.get(12).getMetricIdentifier()).isEqualTo("metric2");
    Map<String, List<TimeSeriesRecord>> instanceMap =
        timeSeriesRecords.stream().collect(Collectors.groupingBy(TimeSeriesRecord::getHostname));
    assertThat(instanceMap.keySet().size()).isEqualTo(2);
    assertThat(instanceMap.get("ip-192-168-23-156.ec2.internal").size()).isEqualTo(12);
    assertThat(instanceMap.get("ip-192-168-59-131.ec2.internal").size()).isEqualTo(12);
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/prometheus/aws/dsl/" + name)),
        StandardCharsets.UTF_8);
  }
}
