/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureRegistrySettingsAdapterTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureDockerHubPublicRegistrySettingsProvider dockerHubPublicRegistrySettingsProvider;
  @Mock private AzureDockerHubPrivateRegistrySettingsProvider dockerHubPrivateRegistrySettingsProvider;
  @Mock private AzureArtifactoryRegistrySettingsProvider artifactoryRegistrySettingsProvider;
  @Mock private AzureContainerRegistrySettingsProvider azureContainerRegistrySettingsProvider;

  @InjectMocks private AzureRegistrySettingsAdapter settingsAdapter;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsDockerHubPublic() {
    final AzureContainerArtifactConfig artifactConfig =
        AzureContainerArtifactConfig.builder().registryType(AzureRegistryType.DOCKER_HUB_PUBLIC).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(dockerHubPublicRegistrySettingsProvider).getContainerSettings(artifactConfig);
    verifyNoMoreInteractions(dockerHubPrivateRegistrySettingsProvider, artifactoryRegistrySettingsProvider,
        azureContainerRegistrySettingsProvider);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsDockerHubPrivate() {
    final AzureContainerArtifactConfig artifactConfig =
        AzureContainerArtifactConfig.builder().registryType(AzureRegistryType.DOCKER_HUB_PRIVATE).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(dockerHubPrivateRegistrySettingsProvider).getContainerSettings(artifactConfig);
    verifyNoMoreInteractions(dockerHubPublicRegistrySettingsProvider, artifactoryRegistrySettingsProvider,
        azureContainerRegistrySettingsProvider);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsArtifactory() {
    final AzureContainerArtifactConfig artifactConfig =
        AzureContainerArtifactConfig.builder().registryType(AzureRegistryType.ARTIFACTORY_PRIVATE_REGISTRY).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(artifactoryRegistrySettingsProvider).getContainerSettings(artifactConfig);
    verifyNoMoreInteractions(dockerHubPrivateRegistrySettingsProvider, dockerHubPublicRegistrySettingsProvider,
        azureContainerRegistrySettingsProvider);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetContainerSettingsAcr() {
    final AzureContainerArtifactConfig artifactConfig =
        AzureContainerArtifactConfig.builder().registryType(AzureRegistryType.ACR).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(azureContainerRegistrySettingsProvider).getContainerSettings(artifactConfig);
    verifyNoMoreInteractions(dockerHubPrivateRegistrySettingsProvider, dockerHubPublicRegistrySettingsProvider,
        artifactoryRegistrySettingsProvider);
  }
}