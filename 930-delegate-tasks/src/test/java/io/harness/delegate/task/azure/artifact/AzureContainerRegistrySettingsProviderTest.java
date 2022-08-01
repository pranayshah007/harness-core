/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.ACR_DEFAULT_DOCKER_USERNAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME;
import static io.harness.delegate.task.azure.AzureTestUtils.REGISTRY_HOSTNAME;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureContainerRegistryClient;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.utility.AzureUtils;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.rule.Owner;

import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureContainerRegistrySettingsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureConnectorMapper connectorMapper;
  @Mock private AzureContainerRegistryClient azureContainerRegistryClient;
  @Mock private AzureAuthorizationClient azureAuthorizationClient;

  @InjectMocks private AzureContainerRegistrySettingsProvider settingsProvider;

  @Mock private AzureConfig azureConfig;

  @Before
  public void setup() {
    doReturn(azureConfig).when(connectorMapper).toAzureConfig(any(AzureConnectorDTO.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsServicePrincipalSecret() {
    final char[] secret = "secret-key".toCharArray();
    final AzureContainerArtifactConfig containerArtifactConfig =
        AzureTestUtils.createTestContainerArtifactConfig(AzureTestUtils.createAzureConnectorDTO());
    doReturn(AzureAuthenticationType.SERVICE_PRINCIPAL_SECRET).when(azureConfig).getAzureAuthenticationType();
    doReturn(secret).when(azureConfig).getKey();
    doReturn(AzureTestUtils.CLIENT_ID).when(azureConfig).getClientId();

    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        settingsProvider.getContainerSettings(containerArtifactConfig);

    assertThat(dockerSettings)
        .containsKeys(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME, DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME,
            DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME);
    assertThat(dockerSettings.values().stream().map(AzureAppServiceApplicationSetting::getValue))
        .containsExactlyInAnyOrder(AzureTestUtils.CLIENT_ID, REGISTRY_HOSTNAME, new String(secret));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsPrincipalCert() {
    testGetContainerSettingsRefreshToken(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsMSI() {
    testGetContainerSettingsRefreshToken(AzureAuthenticationType.MANAGED_IDENTITY_SYSTEM_ASSIGNED);
  }

  private void testGetContainerSettingsRefreshToken(AzureAuthenticationType authenticationType) {
    final String accessToken = "accessToken";
    final String refreshToken = "refreshToken";
    final AzureIdentityAccessTokenResponse accessTokenResponse =
        AzureIdentityAccessTokenResponse.builder().accessToken(accessToken).build();
    final AzureContainerArtifactConfig containerArtifactConfig =
        AzureTestUtils.createTestContainerArtifactConfig(AzureTestUtils.createAzureConnectorDTO());
    doReturn(authenticationType).when(azureConfig).getAzureAuthenticationType();
    doReturn(accessTokenResponse)
        .when(azureAuthorizationClient)
        .getUserAccessToken(azureConfig,
            AzureAuthenticationType.SERVICE_PRINCIPAL_CERT == authenticationType ? AzureUtils.AUTH_SCOPE : null);
    doReturn(refreshToken).when(azureContainerRegistryClient).getAcrRefreshToken(REGISTRY_HOSTNAME, accessToken);

    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        settingsProvider.getContainerSettings(containerArtifactConfig);

    assertThat(dockerSettings)
        .containsKeys(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME, DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME,
            DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME);
    assertThat(dockerSettings.values().stream().map(AzureAppServiceApplicationSetting::getValue))
        .containsExactlyInAnyOrder(ACR_DEFAULT_DOCKER_USERNAME, REGISTRY_HOSTNAME, refreshToken);
  }
}