/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType.NEXUS_PRIVATE_REGISTRY;
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
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TasNexus3RegistrySettingsProviderTest extends CategoryTest {
  @Mock DecryptionHelper decryptionHelper;
  TasNexus3RegistrySettingsProvider tasNexus3RegistrySettingsProvider = new TasNexus3RegistrySettingsProvider();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = RISHABH)
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

    TasArtifactCreds containerSettingsResult = tasNexus3RegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfig(nexusConnectorDTO, NEXUS_PRIVATE_REGISTRY), decryptionHelper);

    assertThat(containerSettingsResult.getPassword()).isEqualTo("test-password");
    assertThat(containerSettingsResult.getUsername()).isEqualTo("test-username");
    assertThat(containerSettingsResult.getUrl()).isEqualTo(REGISTRY_HOSTNAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForNexusContainerRegistryNullUser() {
    NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .nexusServerUrl(null)
            .auth(NexusAuthenticationDTO.builder()
                      .authType(NexusAuthType.USER_PASSWORD)
                      .credentials(NexusUsernamePasswordAuthDTO.builder()
                                       .username(null)
                                       .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'a'}).build())
                                       .build())
                      .build())
            .build();

    assertThatThrownBy(
        ()
            -> tasNexus3RegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(nexusConnectorDTO, NEXUS_PRIVATE_REGISTRY),
                decryptionHelper))
        .hasMessage("Configure username for Nexus Private Registry  container registry ConnectorDisconnectHandler");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForNexusContainerRegistryNullPass() {
    NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .nexusServerUrl(null)
            .auth(NexusAuthenticationDTO.builder()
                      .authType(NexusAuthType.USER_PASSWORD)
                      .credentials(NexusUsernamePasswordAuthDTO.builder()
                                       .username("hello")
                                       .passwordRef(SecretRefData.builder().decryptedValue(new char[] {}).build())
                                       .build())
                      .build())
            .build();

    assertThatThrownBy(
        ()
            -> tasNexus3RegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(nexusConnectorDTO, NEXUS_PRIVATE_REGISTRY),
                decryptionHelper))
        .hasMessage("Configure password for Nexus Private Registry  container registry ConnectorDisconnectHandler");
    assertThatThrownBy(()
                           -> tasNexus3RegistrySettingsProvider.getContainerSettings(
                               TasTestUtils.createTestContainerArtifactConfigWithoutHostname(
                                   nexusConnectorDTO, NEXUS_PRIVATE_REGISTRY),
                               decryptionHelper))
        .hasMessage("Check if ConnectorDisconnectHandler provided Nexus Private Registry  is properly configured");
  }
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForNexusContainerRegistryBlankHostname() {
    NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .nexusServerUrl("host.test-registryName")
            .auth(NexusAuthenticationDTO.builder().authType(NexusAuthType.ANONYMOUS).build())
            .build();

    TasArtifactCreds containerSettingsResult = tasNexus3RegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfigWithoutHostname(nexusConnectorDTO, NEXUS_PRIVATE_REGISTRY),
        decryptionHelper);

    assertThat(containerSettingsResult.getPassword()).isNull();
    assertThat(containerSettingsResult.getUsername()).isNull();
    assertThat(containerSettingsResult.getUrl()).isEqualTo("host.test-registryName");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForNexusContainerRegistryAnonymous() {
    NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .nexusServerUrl("host.test-registryName")
            .auth(NexusAuthenticationDTO.builder().authType(NexusAuthType.ANONYMOUS).build())
            .build();

    TasArtifactCreds containerSettingsResult = tasNexus3RegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfig(nexusConnectorDTO, NEXUS_PRIVATE_REGISTRY), decryptionHelper);

    assertThat(containerSettingsResult.getPassword()).isNull();
    assertThat(containerSettingsResult.getUsername()).isNull();
    assertThat(containerSettingsResult.getUrl()).isEqualTo(REGISTRY_HOSTNAME);
  }
}
