/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.impl.delegate.AwsEcrApiHelperServiceDelegate;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AzureElasticContainerRegistrySettingsProviderTest extends CategoryTest {
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private AwsEcrApiHelperServiceDelegate awsEcrApiHelperServiceDelegate;
  @Mock private SecretDecryptionService secretDecryptionService;
  @InjectMocks AzureElasticContainerRegistrySettingsProvider azureElasticContainerRegistrySettingsProvider;
  public static final String BAZE_64_AWS_ENCODED_TOKEN = "YXdzOnRlc3QtdG9rZW46ZWNy";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VLICA)
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
                                    SecretRefData.builder().decryptedValue("test-secretKey".toCharArray()).build())
                                .build())
                    .build())
            .build();

    doReturn(null).when(secretDecryptionService).decrypt(any(), anyList());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());

    doReturn(BAZE_64_AWS_ENCODED_TOKEN)
        .when(awsEcrApiHelperServiceDelegate)
        .getAmazonEcrAuthToken(any(), any(), anyString());

    Map<String, AzureAppServiceApplicationSetting> containerSettingsResult =
        azureElasticContainerRegistrySettingsProvider.getContainerSettings(
            AzureTestUtils.createTestContainerArtifactConfig(awsConnectorDTO));

    assertThat(containerSettingsResult.size()).isEqualTo(3);
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_URL")).getValue())
        .isEqualTo("https://test.registry.io/");
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_PASSWORD")).getValue()).isEqualTo("test-token");
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_USERNAME")).getValue()).isEqualTo("AWS");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testThatNonManualCredentialsThrowsExceptionWhenGetDockerSettings() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).build())
            .build();

    assertThatThrownBy(()
                           -> azureElasticContainerRegistrySettingsProvider.getContainerSettings(
                               AzureTestUtils.createTestContainerArtifactConfig(awsConnectorDTO)))
        .isInstanceOf(InvalidRequestException.class);
  }
}
