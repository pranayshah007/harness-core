/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureArtifactoryRegistrySettingsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks private AzureArtifactoryRegistrySettingsProvider settingsProvider;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsCredentials() {
    final String artifactoryServerUrl = "artifactory.repo.io";
    final String username = "username";
    final String password = "password";
    final AzureContainerArtifactConfig artifactConfig = AzureTestUtils.createTestContainerArtifactConfig(
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(artifactoryServerUrl)
            .auth(ArtifactoryAuthenticationDTO.builder()
                      .authType(ArtifactoryAuthType.USER_PASSWORD)
                      .credentials(
                          ArtifactoryUsernamePasswordAuthDTO.builder()
                              .username(username)
                              .passwordRef(SecretRefData.builder().decryptedValue(password.toCharArray()).build())
                              .build())
                      .build())
            .build());

    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        settingsProvider.getContainerSettings(artifactConfig);

    assertThat(dockerSettings)
        .containsKeys(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME, DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME,
            DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME);
    assertThat(dockerSettings.values().stream().map(AzureAppServiceApplicationSetting::getValue))
        .containsExactlyInAnyOrder("test.registry.io", username, password);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsAnonymous() {
    final String artifactoryServerUrl = "artifactory.repo.io";
    final AzureContainerArtifactConfig artifactConfig = AzureTestUtils.createTestContainerArtifactConfig(
        ArtifactoryConnectorDTO.builder()
            .artifactoryServerUrl(artifactoryServerUrl)
            .auth(ArtifactoryAuthenticationDTO.builder().authType(ArtifactoryAuthType.ANONYMOUS).build())
            .build());

    Map<String, AzureAppServiceApplicationSetting> dockerSettings =
        settingsProvider.getContainerSettings(artifactConfig);

    assertThat(dockerSettings).containsKeys(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME);
    assertThat(dockerSettings.values().stream().map(AzureAppServiceApplicationSetting::getValue))
        .containsExactlyInAnyOrder("test.registry.io");
  }
}