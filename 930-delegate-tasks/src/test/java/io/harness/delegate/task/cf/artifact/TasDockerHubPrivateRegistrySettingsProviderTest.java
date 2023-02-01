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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

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
public class TasDockerHubPrivateRegistrySettingsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock DecryptionHelper decryptionHelper;
  @InjectMocks private TasDockerHubPrivateRegistrySettingsProvider settingsProvider;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettings() {
    final String dockerRegistryUrl = "hub.docker.com";
    final String username = "username";
    final String password = "password";
    final TasContainerArtifactConfig artifactConfig = TasTestUtils.createTestContainerArtifactConfig(
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
            .build(),
        TasArtifactRegistryType.DOCKER_HUB_PRIVATE);

    TasArtifactCreds dockerSettings = settingsProvider.getContainerSettings(artifactConfig, decryptionHelper);

    assertThat(dockerSettings.getPassword()).isEqualTo(password);
    assertThat(dockerSettings.getUsername()).isEqualTo(username);
    assertThat(dockerSettings.getUrl()).isEqualTo(dockerRegistryUrl);
  }
}