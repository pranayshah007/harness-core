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
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
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
public class AzureDockerHubPrivateRegistrySettingsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks private AzureDockerHubPrivateRegistrySettingsProvider settingsProvider;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettings() {
    final String dockerRegistryUrl = "hub.docker.com";
    final String username = "username";
    final String password = "password";
    final AzureContainerArtifactConfig artifactConfig = AzureTestUtils.createTestContainerArtifactConfig(
        DockerConnectorDTO.builder()
            .dockerRegistryUrl(dockerRegistryUrl)
            .providerType(DockerRegistryProviderType.DOCKER_HUB)
            .auth(DockerAuthenticationDTO.builder()
                      .authType(DockerAuthType.USER_PASSWORD)
                      .credentials(
                          DockerUserNamePasswordDTO.builder()
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
        .containsExactlyInAnyOrder(dockerRegistryUrl, username, password);
  }
}