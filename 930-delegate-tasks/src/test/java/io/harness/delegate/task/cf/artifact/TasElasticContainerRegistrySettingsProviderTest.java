/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TasElasticContainerRegistrySettingsProviderTest extends CategoryTest {
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock DecryptionHelper decryptionHelper;
  @InjectMocks TasElasticContainerRegistrySettingsProvider tasElasticContainerRegistrySettingsProvider;
  public static final String BAZE_64_AWS_ENCODED_TOKEN = "YXdzOnRlc3QtdG9rZW46ZWNy";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    MockitoAnnotations.initMocks(this);
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForAzureElasticContainerRegistry() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKeyRef(
                                    SecretRefData.builder().decryptedValue("test-accessKey".toCharArray()).build())
                                .secretKeyRef(
                                    SecretRefData.builder().decryptedValue("test-accessKey".toCharArray()).build())
                                .build())
                    .build())
            .build();

    doReturn(null).when(secretDecryptionService).decrypt(any(), anyList());
    doReturn(AwsInternalConfig.builder()
                 .accessKey("test-accessKey".toCharArray())
                 .secretKey("test-secretKey".toCharArray())
                 .build())
        .when(awsNgConfigMapper)
        .createAwsInternalConfig(any());

    doReturn(BAZE_64_AWS_ENCODED_TOKEN)
        .when(awsEcrApiHelperServiceDelegate)
        .getAmazonEcrAuthToken(any(), any(), anyString());

    TasArtifactCreds dockerSettings = tasElasticContainerRegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfig(awsConnectorDTO, TasArtifactRegistryType.ECR), decryptionHelper);

    assertThat(dockerSettings.getPassword()).isEqualTo("test-secretKey");
    assertThat(dockerSettings.getUsername()).isEqualTo("test-accessKey");
    assertThat(dockerSettings.getUrl()).isEqualTo("https://test.registry.io/");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testThatNonManualCredentialsThrowsExceptionWhenGetDockerSettings() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).build())
            .build();

    assertThatThrownBy(
        ()
            -> tasElasticContainerRegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(awsConnectorDTO, TasArtifactRegistryType.ECR),
                decryptionHelper))
        .isInstanceOf(InvalidRequestException.class);
  }
}
