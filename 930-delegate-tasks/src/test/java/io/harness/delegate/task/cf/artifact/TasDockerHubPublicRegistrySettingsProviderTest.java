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

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class TasDockerHubPublicRegistrySettingsProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock DecryptionHelper decryptionHelper;
  @InjectMocks private TasDockerHubPublicRegistrySettingsProvider settingsProvider;

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettings() {
    final String dockerRegistryUrl = "hub.docker.com";
    final TasContainerArtifactConfig artifactConfig = TasTestUtils.createTestContainerArtifactConfig(
        DockerConnectorDTO.builder()
            .dockerRegistryUrl(dockerRegistryUrl)
            .providerType(DockerRegistryProviderType.DOCKER_HUB)
            .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
            .build(),
        TasArtifactRegistryType.DOCKER_HUB_PUBLIC);

    TasArtifactCreds dockerSettings = settingsProvider.getContainerSettings(artifactConfig, decryptionHelper);

    assertThat(dockerSettings.getPassword()).isNull();
    assertThat(dockerSettings.getUsername()).isNull();
    assertThat(dockerSettings.getUrl()).isEqualTo(dockerRegistryUrl);
  }
}