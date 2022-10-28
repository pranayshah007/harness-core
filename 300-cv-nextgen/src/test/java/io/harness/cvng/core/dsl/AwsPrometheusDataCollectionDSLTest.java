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
import io.harness.cvng.HoverflyCVNextGenTestBase;
import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.AwsPrometheusCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.PrometheusCVConfig;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsPrometheusDataCollectionDSLTest extends HoverflyCVNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject private MetricPackService metricPackService;
  @Inject private AwsPrometheusDataCollectionInfoMapper dataCollectionInfoMapper;
  private ExecutorService executorService;
  String accessKey;
  String secretKey;
  AwsConnectorDTO testAwsConnector;

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
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  @Ignore("Ignore till there is data in Prometheus")
  public void testExecute_awsPrometheusDSLWithoutData() throws IOException {
    DataCollectionDSLService dataCollectionDSLService = new DataCollectionServiceImpl();
    dataCollectionDSLService.registerDatacollectionExecutorService(executorService);
    String code = readDSL("metric-collection.datacollection");
    Instant instant = Instant.parse("2022-10-27T10:21:00.000Z");
    List<MetricPack> metricPacks = metricPackService.getMetricPacks(builderFactory.getContext().getAccountId(),
        builderFactory.getContext().getOrgIdentifier(), builderFactory.getContext().getProjectIdentifier(),
        DataSourceType.AWS_PROMETHEUS);

    AwsPrometheusCVConfig awsPrometheusCVConfig =
        (AwsPrometheusCVConfig) builderFactory.awsPrometheusCVConfigBuilder()
            .metricInfoList(Collections.singletonList(
                PrometheusCVConfig.MetricInfo.builder()
                    .query("avg(\n"
                        + "\tgauge_servo_response_mvc_createpayment\t{\n"
                        + "\n"
                        + "\t\tjob=\"payment-service-nikpapag\"\n"
                        + "\n"
                        + "})")
                    .metricType(TimeSeriesMetricType.RESP_TIME)
                    .identifier("createpayment")
                    .metricName("createpayment")
                    //                                                          .serviceInstanceFieldName("pod")
                    .isManualQuery(true)
                    .build()))
            .build();
    awsPrometheusCVConfig.setMetricPack(metricPacks.get(0));
    AwsPrometheusDataCollectionInfo awsPrometheusDataCollectionInfo =
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

    assertThat(true).isTrue();
  }

  private String readDSL(String name) throws IOException {
    return FileUtils.readFileToString(
        new File(getSourceResourceFile(AppDynamicsCVConfig.class, "/prometheus/aws/dsl/" + name)),
        StandardCharsets.UTF_8);
  }
}
