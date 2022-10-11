/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class CloudWatchMetricDataCollectionInfoTest extends CategoryTest {
  String name;
  String region;
  String groupName;
  String expression;
  String identifier;
  String serviceInstancePath;
  String accessKey;
  String secretKey;
  CloudWatchMetricDataCollectionInfo dataCollectionInfo;
  AwsConnectorDTO testAwsConnector;

  @Before
  public void setup() {
    name = "some-name";
    region = "us-east1";
    groupName = "g1";
    expression = "expression";
    identifier = "identifier";
    serviceInstancePath = "path";
    accessKey = "accessKey";
    secretKey = "secretKey";
    List<CloudWatchMetricInfoDTO> metricInfoDTOs = new ArrayList<>();
    CloudWatchMetricInfoDTO infoDTO1 =
        CloudWatchMetricInfoDTO.builder()
            .metricName(name)
            .metricIdentifier(identifier)
            .expression(expression)
            .finalExpression(expression)
            .responseMapping(MetricResponseMappingDTO.builder().serviceInstanceJsonPath(serviceInstancePath).build())
            .build();
    CloudWatchMetricInfoDTO infoDTO2 =
        CloudWatchMetricInfoDTO.builder()
            .metricName(name + "2")
            .metricIdentifier(identifier + "2")
            .expression(expression + "2")
            .finalExpression(expression + "2")
            .responseMapping(
                MetricResponseMappingDTO.builder().serviceInstanceJsonPath(serviceInstancePath + "2").build())
            .build();
    metricInfoDTOs.add(infoDTO1);
    metricInfoDTOs.add(infoDTO2);
    dataCollectionInfo = CloudWatchMetricDataCollectionInfo.builder()
                             .region(region)
                             .groupName(groupName)
                             .metricInfos(metricInfoDTOs)
                             .metricPack(null)
                             .build();
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
  public void testGetBaseUrl() {
    assertThat(dataCollectionInfo.getBaseUrl(testAwsConnector)).isEqualTo("https://monitoring.us-east1.amazonaws.com");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectionHeaders() {
    assertThat(dataCollectionInfo.collectionHeaders(testAwsConnector)).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectionParams() {
    assertThat(dataCollectionInfo.collectionParams(testAwsConnector)).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withoutHostDataCollection() {
    dataCollectionInfo.setCollectHostData(false);
    Map<String, Object> envVariables = dataCollectionInfo.getDslEnvVariables(testAwsConnector);
    assertCommons(envVariables);
    List<List<Map<String, Object>>> requestBodies = (List<List<Map<String, Object>>>) envVariables.get("bodies");
    assertThat(requestBodies.size()).isEqualTo(2);
    assertThat(requestBodies.get(0).get(0).get("Expression")).isEqualTo(expression);
    assertThat(requestBodies.get(0).get(0).get("Label")).isEqualTo(name);
    assertThat(requestBodies.get(0).get(0).get("Id")).isEqualTo(identifier);
    assertThat(requestBodies.get(0).get(0).get("Period")).isEqualTo(60);
    assertThat(envVariables.get("collectHostData")).isEqualTo(false);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withHostDataCollection() {
    dataCollectionInfo.setCollectHostData(true);
    Map<String, Object> envVariables = dataCollectionInfo.getDslEnvVariables(testAwsConnector);
    assertCommons(envVariables);
    List<List<Map<String, Object>>> requestBodies = (List<List<Map<String, Object>>>) envVariables.get("bodies");
    assertThat(requestBodies.size()).isEqualTo(2);
    String finalExpression = expression + " GROUP BY "
        + dataCollectionInfo.getMetricInfos().get(0).getResponseMapping().getServiceInstanceJsonPath();
    assertThat(requestBodies.get(0).get(0).get("Expression")).isEqualTo(finalExpression);
    assertThat(requestBodies.get(0).get(0).get("Label")).isEqualTo(name);
    assertThat(requestBodies.get(0).get(0).get("Id")).isEqualTo(identifier);
    assertThat(requestBodies.get(0).get(0).get("Period")).isEqualTo(60);
    assertThat(envVariables.get("collectHostData")).isEqualTo(true);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_orderOfMetricNamesAndIdentifiers() {
    dataCollectionInfo.setCollectHostData(true);
    Map<String, Object> envVariables = dataCollectionInfo.getDslEnvVariables(testAwsConnector);
    assertCommons(envVariables);
    List<List<Map<String, Object>>> requestBodies = (List<List<Map<String, Object>>>) envVariables.get("bodies");
    assertThat(requestBodies.size()).isEqualTo(2);
    List<String> metricNames = (List<String>) envVariables.get("metricNames");
    List<String> metricIdentifiers = (List<String>) envVariables.get("metricIdentifiers");
    assertThat(metricNames).hasSize(2);
    assertThat(metricNames.get(0)).isEqualTo(requestBodies.get(0).get(0).get("Label"));
    assertThat(metricNames.get(1)).isEqualTo(requestBodies.get(1).get(0).get("Label"));
    assertThat(metricIdentifiers).hasSize(2);
    assertThat(metricIdentifiers.get(0)).isEqualTo(requestBodies.get(0).get(0).get("Id"));
    assertThat(metricIdentifiers.get(1)).isEqualTo(requestBodies.get(1).get(0).get("Id"));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withEncryptedAccessKey() {
    AwsConnectorDTO awsConnector =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey(null)
                                .accessKeyRef(SecretRefData.builder().decryptedValue(accessKey.toCharArray()).build())
                                .secretKeyRef(SecretRefData.builder().decryptedValue(secretKey.toCharArray()).build())
                                .build())
                    .build())
            .build();
    Map<String, Object> envVariables = dataCollectionInfo.getDslEnvVariables(awsConnector);
    assertThat(envVariables.get("awsAccessKey")).isEqualTo(accessKey);
  }

  private void assertCommons(Map<String, Object> envVariables) {
    assertThat(envVariables.get("region")).isEqualTo(region);
    assertThat(envVariables.get("groupName")).isEqualTo(groupName);
    assertThat(envVariables.get("awsSecretKey")).isEqualTo(secretKey);
    assertThat(envVariables.get("awsAccessKey")).isEqualTo(accessKey);
    assertThat(envVariables.get("serviceName")).isEqualTo("monitoring");
    assertThat(envVariables.get("url")).isEqualTo("https://monitoring.us-east1.amazonaws.com");
    assertThat(envVariables.get("awsTarget")).isEqualTo("GraniteServiceVersion20100801.GetMetricData");
    assertThat(envVariables.get("body")).isEqualTo(null);
  }
}
