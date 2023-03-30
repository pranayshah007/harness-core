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
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.cvng.utils.AwsUtils.AwsAccessKeys;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsIRSASpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;

public class AwsUtilsTest extends CvNextGenTestBase {
  private AwsConnectorDTO manualCredentialConnectorDto;
  private AwsConnectorDTO manualWithCrossAccountAccessCredentialConnectorDto;
  private AwsConnectorDTO irsaCredentialConnectorDto;
  private AwsConnectorDTO inheritFromDelegateCredentialConnectorDto;
  private String accessKeyId;
  private String secretKey;
  private char[] decryptedAccessKeyId;
  private char[] decryptedSecretKey;

  private String crossAccountRoleArn;
  private String externalId;

  @Before
  public void setup() {
    crossAccountRoleArn = "crossAccountRoleArn";
    externalId = "externalId";
    accessKeyId = "a1";
    secretKey = "s1";
    decryptedAccessKeyId = accessKeyId.toCharArray();
    decryptedSecretKey = secretKey.toCharArray();
    AwsManualConfigSpecDTO awsManualConfigSpecDTO =
        AwsManualConfigSpecDTO.builder()
            .accessKey(accessKeyId)
            .secretKeyRef(SecretRefData.builder().decryptedValue(decryptedSecretKey).build())
            .build();
    manualCredentialConnectorDto = AwsConnectorDTO.builder()
                                       .credential(AwsCredentialDTO.builder()
                                                       .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                       .config(awsManualConfigSpecDTO)
                                                       .build())
                                       .build();
    manualWithCrossAccountAccessCredentialConnectorDto =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                            .config(awsManualConfigSpecDTO)
                            .crossAccountAccess(CrossAccountAccessDTO.builder()
                                                    .crossAccountRoleArn(crossAccountRoleArn)
                                                    .externalId(externalId)
                                                    .build())
                            .build())
            .build();
    irsaCredentialConnectorDto = AwsConnectorDTO.builder()
                                     .credential(AwsCredentialDTO.builder()
                                                     .awsCredentialType(AwsCredentialType.IRSA)
                                                     .config(AwsIRSASpecDTO.builder().build())
                                                     .build())
                                     .build();
    inheritFromDelegateCredentialConnectorDto =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                            .config(AwsInheritFromDelegateSpecDTO.builder().build())
                            .build())
            .build();
  }
  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetBaseUrl() {
    String baseUrl = AwsUtils.getBaseUrl("region1", "service1");
    assertThat(baseUrl).isEqualTo("https://service1.region1.amazonaws.com");
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAwsCredentials_manualCredentials() {
    AwsAccessKeys awsCredentials = AwsUtils.getAwsCredentials(manualCredentialConnectorDto);
    assertThat(awsCredentials.getSecretAccessKey()).isEqualTo(secretKey);
    assertThat(awsCredentials.getAccessKeyId()).isEqualTo(accessKeyId);
    assertThat(awsCredentials.getSessionToken()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateAwsInternalConfig_manualCredentials() {
    AwsInternalConfig awsInternalConfig = AwsUtils.createAwsInternalConfig(manualCredentialConnectorDto);
    assertThat(awsInternalConfig.isUseEc2IamCredentials()).isFalse();
    assertThat(awsInternalConfig.isUseIRSA()).isFalse();
    assertThat(awsInternalConfig.isAssumeCrossAccountRole()).isFalse();
    assertThat(awsInternalConfig.getAccessKey()).isEqualTo(decryptedAccessKeyId);
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo(decryptedSecretKey);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateAwsInternalConfig_manualCredentials_withSTSEnabled() {
    AwsInternalConfig awsInternalConfig =
        AwsUtils.createAwsInternalConfig(manualWithCrossAccountAccessCredentialConnectorDto);
    assertThat(awsInternalConfig.isUseEc2IamCredentials()).isFalse();
    assertThat(awsInternalConfig.isUseIRSA()).isFalse();
    assertThat(awsInternalConfig.isAssumeCrossAccountRole()).isTrue();
    assertThat(awsInternalConfig.getAccessKey()).isEqualTo(decryptedAccessKeyId);
    assertThat(awsInternalConfig.getSecretKey()).isEqualTo(decryptedSecretKey);
    assertThat(awsInternalConfig.getCrossAccountAttributes().getCrossAccountRoleArn()).isEqualTo(crossAccountRoleArn);
    assertThat(awsInternalConfig.getCrossAccountAttributes().getExternalId()).isEqualTo(externalId);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateAwsInternalConfig_irsaCredentials() {
    AwsInternalConfig awsInternalConfig = AwsUtils.createAwsInternalConfig(irsaCredentialConnectorDto);
    assertThat(awsInternalConfig.isUseEc2IamCredentials()).isFalse();
    assertThat(awsInternalConfig.isUseIRSA()).isTrue();
    assertThat(awsInternalConfig.isAssumeCrossAccountRole()).isFalse();
    assertThat(awsInternalConfig.getAccessKey()).isNull();
    assertThat(awsInternalConfig.getSecretKey()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateAwsInternalConfig_inheritFromDelegateCredentials() {
    AwsInternalConfig awsInternalConfig = AwsUtils.createAwsInternalConfig(inheritFromDelegateCredentialConnectorDto);
    assertThat(awsInternalConfig.isUseEc2IamCredentials()).isTrue();
    assertThat(awsInternalConfig.isUseIRSA()).isFalse();
    assertThat(awsInternalConfig.isAssumeCrossAccountRole()).isFalse();
    assertThat(awsInternalConfig.getAccessKey()).isNull();
    assertThat(awsInternalConfig.getSecretKey()).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAwsCredentialsProvider_manualCredentials() {
    AwsInternalConfig awsInternalConfig = AwsUtils.createAwsInternalConfig(manualCredentialConnectorDto);
    AwsCredentialsProvider awsCredentialsProvider = AwsUtils.getAwsCredentialsProvider(awsInternalConfig);
    assertThat(awsCredentialsProvider).isInstanceOf(StaticCredentialsProvider.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAwsCredentialsProvider_manualCredentials_withSTSEnabled() {
    AwsInternalConfig awsInternalConfig =
        AwsUtils.createAwsInternalConfig(manualWithCrossAccountAccessCredentialConnectorDto);
    AwsCredentialsProvider awsCredentialsProvider = AwsUtils.getAwsCredentialsProvider(awsInternalConfig);
    assertThat(awsCredentialsProvider).isInstanceOf(StsAssumeRoleCredentialsProvider.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAwsCredentialsProvider_irsaCredentials() {
    AwsInternalConfig awsInternalConfig = AwsUtils.createAwsInternalConfig(irsaCredentialConnectorDto);
    AwsCredentialsProvider awsCredentialsProvider = AwsUtils.getAwsCredentialsProvider(awsInternalConfig);
    assertThat(awsCredentialsProvider).isInstanceOf(WebIdentityTokenFileCredentialsProvider.class);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetAwsCredentialsProvider_inheritFromDelegateCredentials_absentURI() {
    AwsInternalConfig awsInternalConfig = AwsUtils.createAwsInternalConfig(inheritFromDelegateCredentialConnectorDto);
    AwsCredentialsProvider awsCredentialsProvider = AwsUtils.getAwsCredentialsProvider(awsInternalConfig);
    assertThat(awsCredentialsProvider).isInstanceOf(InstanceProfileCredentialsProvider.class);
  }
}
