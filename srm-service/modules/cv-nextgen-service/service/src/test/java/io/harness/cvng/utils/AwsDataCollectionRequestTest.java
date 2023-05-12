/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.beans.aws.AwsDataCollectionRequest;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsDataCollectionRequestTest extends CvNextGenTestBase {
  private String accessKeyId;
  private String secretKey;
  private String region;
  private String service;
  private AwsDataCollectionRequest awsDataCollectionRequest;
  private Map<String, String> queryParameters;
  @Before
  public void setup() {
    accessKeyId = "a1";
    secretKey = "s1";
    char[] decryptedSecretKey = secretKey.toCharArray();
    region = "region";
    service = "service";

    queryParameters = new HashMap<>();
    queryParameters.put("k1", "v1");
    String urlServicePrefix = "urlServicePrefix";
    String urlServiceSuffix = "urlServiceSuffix";
    AwsManualConfigSpecDTO awsManualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder()
            .accessKey(accessKeyId)
            .secretKeyRef(SecretRefData.builder().decryptedValue(decryptedSecretKey).build())
            .build();
    AwsConnectorDTO manualCredentialConnectorDto =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                            .config(awsManualConfigSpecDTO)
                            .build())
            .build();
    awsDataCollectionRequest = AwsDataCollectionRequest.builder()
                                   .awsService(service)
                                   .region(region)
                                   .queryParameters(queryParameters)
                                   .urlServicePrefix(urlServicePrefix)
                                   .urlServiceSuffix(urlServiceSuffix)
                                   .connectorInfoDTO(ConnectorInfoDTO.builder()
                                                         .name("name")
                                                         .identifier("identifier")
                                                         .connectorType(ConnectorType.AWS)
                                                         .connectorConfig(manualCredentialConnectorDto)
                                                         .build())
                                   .build();
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    awsDataCollectionRequest.setUrlServiceSuffix(null);
    String baseUrl = awsDataCollectionRequest.getBaseUrl();
    assertThat(baseUrl).isEqualTo("https://urlServicePrefix.region.amazonaws.com");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetBaseUrl_withSuffix() {
    String baseUrl = awsDataCollectionRequest.getBaseUrl();
    assertThat(baseUrl).isEqualTo("https://urlServicePrefix.region.amazonaws.com/urlServiceSuffix");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testHeaders_areEmpty() {
    Map<String, String> collectionHeaders = awsDataCollectionRequest.collectionHeaders();
    assertThat(collectionHeaders).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testFetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = awsDataCollectionRequest.fetchDslEnvVariables();
    assertThat(dslEnvVariables).containsEntry("serviceName", service);
    assertThat(dslEnvVariables).containsEntry("region", region);
    assertThat(dslEnvVariables).containsEntry("awsSecretKey", secretKey);
    assertThat(dslEnvVariables).containsEntry("awsAccessKey", accessKeyId);
    assertThat(dslEnvVariables).containsEntry("awsSecurityToken", null);
    assertThat(dslEnvVariables).containsEntry("url", "https://urlServicePrefix.region.amazonaws.com/urlServiceSuffix");
    assertThat(dslEnvVariables).containsEntry("queryMap", queryParameters);
  }
}
