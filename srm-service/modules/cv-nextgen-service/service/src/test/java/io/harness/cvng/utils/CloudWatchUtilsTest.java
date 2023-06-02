/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CloudWatchMetricDataCollectionInfo.CloudWatchMetricInfoDTO;
import io.harness.cvng.beans.MetricResponseMappingDTO;
import io.harness.cvng.utils.AwsUtils.AwsAccessKeys;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class CloudWatchUtilsTest extends CvNextGenTestBase {
  private AwsConnectorDTO awsConnectorDto;
  private String accessKeyId;
  private String secretKey;
  private String metricIdentifier;
  private String metricName;
  private String group;
  private String region;
  private String service;
  private String expression;
  private String sessionToken;
  private AwsAccessKeys awsAccessKeys;
  private boolean collectHostData;
  private List<CloudWatchMetricInfoDTO> cloudWatchMetricInfoDTOs;

  @Before
  public void setup() {
    accessKeyId = "a1";
    secretKey = "s1";
    sessionToken = "st";
    metricIdentifier = "metricIdentifier";
    metricName = "metricName";
    group = "group";
    region = "region";
    service = "service";
    expression = "expression";
    collectHostData = false;
    awsConnectorDto = AwsConnectorDTO.builder().build();
    cloudWatchMetricInfoDTOs = new ArrayList<>();
    CloudWatchMetricInfoDTO cloudWatchMetricInfoDTO =
        CloudWatchMetricInfoDTO.builder()
            .metricIdentifier(metricIdentifier)
            .expression(expression)
            .metricName(metricName)
            .finalExpression(expression)
            .responseMapping(MetricResponseMappingDTO.builder().serviceInstanceJsonPath("p1").build())
            .build();
    cloudWatchMetricInfoDTOs.add(cloudWatchMetricInfoDTO);
    cloudWatchMetricInfoDTOs.add(cloudWatchMetricInfoDTO);
    awsAccessKeys =
        AwsAccessKeys.builder().secretAccessKey(secretKey).accessKeyId(accessKeyId).sessionToken(sessionToken).build();
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    String baseUrl = CloudWatchUtils.getBaseUrl(region, service);
    assertThat(baseUrl).isEqualTo("https://service.region.amazonaws.com");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withExpresssion() {
    try (MockedStatic<AwsUtils> utilities = Mockito.mockStatic(AwsUtils.class)) {
      utilities.when(() -> AwsUtils.getAwsCredentials(any())).thenReturn(awsAccessKeys);
      Map<String, Object> dslEnvVariables = CloudWatchUtils.getDslEnvVariables(
          region, group, expression, metricName, metricIdentifier, service, awsConnectorDto, collectHostData);
      assertCommonParams(dslEnvVariables);
      List<Map<String, Object>> requestBodies = (List<Map<String, Object>>) dslEnvVariables.get("body");
      assertThat(requestBodies).hasSize(1);
      Map<String, Object> requestBody = requestBodies.get(0);
      assertThat(requestBody.get("Expression")).isEqualTo(expression);
      assertThat(requestBody.get("Label")).isEqualTo(metricName);
      assertThat(requestBody.get("Id")).isEqualTo(metricIdentifier);
      assertThat(requestBody.get("Period")).isEqualTo(60);
      assertThat(dslEnvVariables).doesNotContainKey("bodies");
      assertThat(dslEnvVariables).doesNotContainKey("metricNames");
      assertThat(dslEnvVariables).doesNotContainKey("metricIdentifiers");
    }
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withCloudWatchMetricInfoDTO() {
    try (MockedStatic<AwsUtils> utilities = Mockito.mockStatic(AwsUtils.class)) {
      utilities.when(() -> AwsUtils.getAwsCredentials(any())).thenReturn(awsAccessKeys);
      Map<String, Object> dslEnvVariables = CloudWatchUtils.getDslEnvVariables(
          region, group, service, awsConnectorDto, cloudWatchMetricInfoDTOs, collectHostData);
      assertCommonParams(dslEnvVariables);
      List<List<Map<String, Object>>> requestBodies = (List<List<Map<String, Object>>>) dslEnvVariables.get("bodies");
      assertThat(requestBodies).hasSize(2);
      List<Map<String, Object>> requestBody = requestBodies.get(1);
      assertThat(requestBody.get(0).get("Expression")).isEqualTo(expression);
      assertThat(requestBody.get(0).get("Label")).isEqualTo(metricName);
      assertThat(requestBody.get(0).get("Id")).isEqualTo(metricIdentifier);
      assertThat(requestBody.get(0).get("Period")).isEqualTo(60);
      List<String> metricNames = (List<String>) dslEnvVariables.get("metricNames");
      assertThat(metricNames).hasSize(2);
      List<String> metricIdentifiers = (List<String>) dslEnvVariables.get("metricIdentifiers");
      assertThat(metricIdentifiers).hasSize(2);
      assertThat(dslEnvVariables).doesNotContainKey("body");
    }
  }

  private void assertCommonParams(Map<String, Object> dslEnvVariables) {
    assertThat(dslEnvVariables).containsEntry("region", region);
    assertThat(dslEnvVariables).containsEntry("groupName", group);
    assertThat(dslEnvVariables).containsEntry("awsSecretKey", secretKey);
    assertThat(dslEnvVariables).containsEntry("awsAccessKey", accessKeyId);
    assertThat(dslEnvVariables).containsEntry("awsSecurityToken", sessionToken);
    assertThat(dslEnvVariables).containsEntry("serviceName", service);
    assertThat(dslEnvVariables).containsEntry("url", "https://service.region.amazonaws.com");
    assertThat(dslEnvVariables).containsEntry("awsTarget", CloudWatchUtils.CLOUDWATCH_GET_METRIC_DATA_API_TARGET);
    assertThat(dslEnvVariables).containsEntry("collectHostData", collectHostData);
  }
}
