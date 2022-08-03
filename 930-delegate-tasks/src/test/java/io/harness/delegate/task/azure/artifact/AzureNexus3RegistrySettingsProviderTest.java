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

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AzureNexus3RegistrySettingsProviderTest extends CategoryTest {
  AzureNexus3RegistrySettingsProvider azureNexus3RegistrySettingsProvider = new AzureNexus3RegistrySettingsProvider();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForNexusContainerRegistry() {
    NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .nexusServerUrl("host.test-registryName")
            .auth(
                NexusAuthenticationDTO.builder()
                    .authType(NexusAuthType.USER_PASSWORD)
                    .credentials(
                        NexusUsernamePasswordAuthDTO.builder()
                            .username("test-username")
                            .usernameRef(SecretRefData.builder().decryptedValue("test-username".toCharArray()).build())
                            .passwordRef(SecretRefData.builder().decryptedValue("test-password".toCharArray()).build())
                            .build())
                    .build())
            .build();

    Map<String, AzureAppServiceApplicationSetting> containerSettingsResult =
        azureNexus3RegistrySettingsProvider.getContainerSettings(
            AzureTestUtils.createTestContainerArtifactConfig(nexusConnectorDTO));

    assertThat(containerSettingsResult.size()).isEqualTo(3);
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_URL")).getValue()).isEqualTo("test.registry.io");
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_PASSWORD")).getValue()).isEqualTo("test-password");
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_USERNAME")).getValue()).isEqualTo("test-username");
  }
}
