/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.AwsPrometheusDataCollectionInfo.MetricCollectionInfo;
import io.harness.cvng.utils.AwsUtils;
import io.harness.cvng.utils.AwsUtils.AwsAccessKeys;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AwsPrometheusDataCollectionInfoTest extends CategoryTest {
  String accessKeyId;
  String secretKey;
  String sessionToken;
  String region;
  String groupName;
  AwsAccessKeys awsAccessKeys;
  AwsPrometheusDataCollectionInfo awsPrometheusDataCollectionInfo;
  AwsConnectorDTO testAwsConnector;

  @Before
  public void setup() {
    accessKeyId = "a1";
    secretKey = "s1";
    sessionToken = "st";
    region = "r1";
    groupName = "g";
    awsAccessKeys =
        AwsAccessKeys.builder().secretAccessKey(secretKey).accessKeyId(accessKeyId).sessionToken(sessionToken).build();
    awsPrometheusDataCollectionInfo =
        AwsPrometheusDataCollectionInfo.builder()
            .region(region)
            .groupName(groupName)
            .metricCollectionInfoList(Collections.singletonList(MetricCollectionInfo.builder()
                                                                    .metricIdentifier("metricIdentifier")
                                                                    .metricName("metricName")
                                                                    .filters("filters")
                                                                    .query("query")
                                                                    .serviceInstanceField("serviceInstanceField")
                                                                    .build()))
            .workspaceId("workspaceId")
            .build();
    testAwsConnector =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey(accessKeyId)
                                .secretKeyRef(SecretRefData.builder().decryptedValue(secretKey.toCharArray()).build())
                                .build())
                    .build())
            .build();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    assertThat(awsPrometheusDataCollectionInfo.getBaseUrl(testAwsConnector))
        .isEqualTo("https://aps-workspaces.r1.amazonaws.com/workspaces/workspaceId/api/v1/");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectionHeaders() {
    assertThat(awsPrometheusDataCollectionInfo.collectionHeaders(testAwsConnector)).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCollectionParams() {
    assertThat(awsPrometheusDataCollectionInfo.collectionParams(testAwsConnector)).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withoutHostDataCollection() {
    try (MockedStatic<AwsUtils> utilities = Mockito.mockStatic(AwsUtils.class)) {
      utilities.when(() -> AwsUtils.getAwsCredentials(any())).thenReturn(awsAccessKeys);
      utilities.when(() -> AwsUtils.getBaseUrl(any(), any())).thenReturn("baseUrl");
      awsPrometheusDataCollectionInfo.setCollectHostData(false);
      Map<String, Object> envVariables = awsPrometheusDataCollectionInfo.getDslEnvVariables(testAwsConnector);
      assertCommons(envVariables);
      assertThat((List<String>) envVariables.get("baseUrlsForHostCollection")).isEmpty();
      assertThat((List<String>) envVariables.get("serviceInstanceFieldList")).isEmpty();
      assertThat((List<String>) envVariables.get("filterList")).isEmpty();
      assertThat(envVariables.get("collectHostData")).isEqualTo("false");
    }
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables_withHostDataCollection() {
    try (MockedStatic<AwsUtils> utilities = Mockito.mockStatic(AwsUtils.class)) {
      utilities.when(() -> AwsUtils.getAwsCredentials(any())).thenReturn(awsAccessKeys);
      utilities.when(() -> AwsUtils.getBaseUrl(any(), any())).thenReturn("baseUrl");
      awsPrometheusDataCollectionInfo.setCollectHostData(true);
      Map<String, Object> envVariables = awsPrometheusDataCollectionInfo.getDslEnvVariables(testAwsConnector);
      assertCommons(envVariables);
      assertThat(((List<String>) envVariables.get("baseUrlsForHostCollection")).get(0))
          .isEqualTo("baseUrl/workspaces/workspaceId/api/v1/label/serviceInstanceField/values");
      assertThat(((List<String>) envVariables.get("serviceInstanceFieldList")).get(0))
          .isEqualTo("serviceInstanceField");
      assertThat(((List<String>) envVariables.get("filterList")).get(0)).isEqualTo("filters");
      assertThat(envVariables.get("collectHostData")).isEqualTo("true");
    }
  }

  private void assertCommons(Map<String, Object> envVariables) {
    assertThat(envVariables.get("serviceName")).isEqualTo("aps");
    assertThat(envVariables.get("maximumHostSizeAllowed")).isEqualTo(100);
    assertThat(envVariables.get("region")).isEqualTo(region);
    assertThat(envVariables.get("groupName")).isEqualTo(groupName);
    assertThat(envVariables.get("awsSecretKey")).isEqualTo(secretKey);
    assertThat(envVariables.get("awsAccessKey")).isEqualTo(accessKeyId);
    assertThat(envVariables.get("awsSecurityToken")).isEqualTo(sessionToken);
    assertThat(envVariables.get("baseUrlForDataCollection"))
        .isEqualTo("baseUrl/workspaces/workspaceId/api/v1/query_range");
    assertThat(((List<String>) envVariables.get("queryList")).get(0)).isEqualTo("query");
    assertThat(((List<String>) envVariables.get("metricNameList")).get(0)).isEqualTo("metricName");
    assertThat(((List<String>) envVariables.get("metricIdentifiers")).get(0)).isEqualTo("metricIdentifier");
  }
}
