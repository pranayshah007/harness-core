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

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.azure.AzureTestUtils;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AzureGoogleContainerRegistrySettingsProviderTest extends CategoryTest {
  AzureGoogleContainerRegistrySettingsProvider azureGoogleContainerRegistrySettingsProvider =
      new AzureGoogleContainerRegistrySettingsProvider();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForGoogleContainerRegistry() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder()
                    .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                    .config(GcpManualDetailsDTO.builder()
                                .secretKeyRef(
                                    SecretRefData.builder().decryptedValue("test-secretKey".toCharArray()).build())
                                .build())
                    .build())
            .build();

    Map<String, AzureAppServiceApplicationSetting> containerSettingsResult =
        azureGoogleContainerRegistrySettingsProvider.getContainerSettings(
            AzureTestUtils.createTestContainerArtifactConfig(gcpConnectorDTO));

    assertThat(containerSettingsResult.size()).isEqualTo(3);
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_URL")).getValue()).isEqualTo("test.registry.io");
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_PASSWORD")).getValue()).isEqualTo("test-secretKey");
    assertThat((containerSettingsResult.get("DOCKER_REGISTRY_SERVER_USERNAME")).getValue()).isEqualTo("_json_key");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testThatNonManualCredentialsThrowsExceptionWhenGetDockerSettings() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    assertThatThrownBy(()
                           -> azureGoogleContainerRegistrySettingsProvider.getContainerSettings(
                               AzureTestUtils.createTestContainerArtifactConfig(gcpConnectorDTO)))
        .isInstanceOf(InvalidRequestException.class);
  }
}
