/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.azure.AzureTestUtils.REGISTRY_HOSTNAME;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TasGoogleContainerRegistrySettingsProviderTest extends CategoryTest {
  @Mock DecryptionHelper decryptionHelper;
  TasGoogleContainerRegistrySettingsProvider azureGoogleContainerRegistrySettingsProvider =
      new TasGoogleContainerRegistrySettingsProvider();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = RISHABH)
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

    TasArtifactCreds containerSettingsResult = azureGoogleContainerRegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfig(gcpConnectorDTO, TasArtifactRegistryType.GCR), decryptionHelper);

    assertThat(containerSettingsResult.getPassword()).isEqualTo("test-secretKey");
    assertThat(containerSettingsResult.getUsername()).isEqualTo("_json_key");
    assertThat(containerSettingsResult.getUrl()).isEqualTo(REGISTRY_HOSTNAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testThatNonManualCredentialsThrowsExceptionWhenGetDockerSettings() {
    GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(
                GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build())
            .build();

    assertThatThrownBy(
        ()
            -> azureGoogleContainerRegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(gcpConnectorDTO, TasArtifactRegistryType.GCR),
                decryptionHelper))
        .isInstanceOf(InvalidRequestException.class);
  }
}
