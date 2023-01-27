/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.ACR_DEFAULT_DOCKER_USERNAME;
import static io.harness.delegate.task.azure.AzureTestUtils.REGISTRY_HOSTNAME;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureUtils;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.rule.Owner;

import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class TasContainerRegistrySettingsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock DecryptionHelper decryptionHelper;
  @Mock private AzureConnectorMapper connectorMapper;
  @Mock private AzureContainerRegistryClient azureContainerRegistryClient;
  @Mock private AzureAuthorizationClient azureAuthorizationClient;

  @InjectMocks private TasContainerRegistrySettingsProvider settingsProvider;

  @Mock private AzureConfig azureConfig;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(azureConfig).when(connectorMapper).toAzureConfig(any(AzureConnectorDTO.class));
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsServicePrincipalSecret() {
    final char[] secret = "secret-key".toCharArray();
    final TasContainerArtifactConfig containerArtifactConfig = TasTestUtils.createTestContainerArtifactConfig(
        AzureTestUtils.createAzureConnectorDTO(), TasArtifactRegistryType.ACR);
    doReturn(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET).when(azureConfig).getAzureAuthenticationType();
    doReturn(secret).when(azureConfig).getKey();
    doReturn(AzureTestUtils.CLIENT_ID).when(azureConfig).getClientId();

    TasArtifactCreds dockerSettings = settingsProvider.getContainerSettings(containerArtifactConfig, decryptionHelper);

    assertThat(dockerSettings.getPassword()).isEqualTo("secret-key");
    assertThat(dockerSettings.getUsername()).isEqualTo(AzureTestUtils.CLIENT_ID);
    assertThat(dockerSettings.getUrl()).isEqualTo(REGISTRY_HOSTNAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsPrincipalCert() {
    testGetContainerSettingsRefreshToken(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsMSI() {
    testGetContainerSettingsRefreshToken(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED);
  }

  private void testGetContainerSettingsRefreshToken(AzureAuthenticationType authenticationType) {
    final String accessToken = "accessToken";
    final String refreshToken = "refreshToken";
    final AzureIdentityAccessTokenResponse accessTokenResponse =
        AzureIdentityAccessTokenResponse.builder().accessToken(accessToken).build();
    final TasContainerArtifactConfig containerArtifactConfig = TasTestUtils.createTestContainerArtifactConfig(
        AzureTestUtils.createAzureConnectorDTO(), TasArtifactRegistryType.ACR);
    doReturn(authenticationType).when(azureConfig).getAzureAuthenticationType();
    when(azureAuthorizationClient.getUserAccessToken(azureConfig,
             AzureUtils.convertToScope(
                 AzureUtils.getAzureEnvironment(azureConfig.getAzureEnvironmentType()).getManagementEndpoint())))
        .thenReturn(accessTokenResponse);
    doReturn(refreshToken).when(azureContainerRegistryClient).getAcrRefreshToken(REGISTRY_HOSTNAME, accessToken);

    TasArtifactCreds dockerSettings = settingsProvider.getContainerSettings(containerArtifactConfig, decryptionHelper);

    assertThat(dockerSettings.getPassword()).isEqualTo(refreshToken);
    assertThat(dockerSettings.getUsername()).isEqualTo(ACR_DEFAULT_DOCKER_USERNAME);
    assertThat(dockerSettings.getUrl()).isEqualTo(REGISTRY_HOSTNAME);
  }
}