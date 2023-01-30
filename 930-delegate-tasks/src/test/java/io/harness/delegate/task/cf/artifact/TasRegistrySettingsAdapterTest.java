/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
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
public class TasRegistrySettingsAdapterTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock DecryptionHelper decryptionHelper;
  @Mock private TasDockerHubPublicRegistrySettingsProvider tasDockerHubPublicRegistrySettingsProvider;
  @Mock private TasDockerHubPrivateRegistrySettingsProvider tasDockerHubPrivateRegistrySettingsProvider;
  @Mock private TasArtifactoryRegistrySettingsProvider tasArtifactoryRegistrySettingsProvider;
  @Mock private TasContainerRegistrySettingsProvider tasContainerRegistrySettingsProvider;
  @Mock private TasElasticContainerRegistrySettingsProvider tasElasticContainerRegistrySettingsProvider;
  @Mock private TasGoogleContainerRegistrySettingsProvider tasGoogleContainerRegistrySettingsProvider;
  @Mock private TasGoogleArtifactRegistrySettingsProvider tasGoogleArtifactRegistrySettingsProvider;
  @Mock private TasNexus3RegistrySettingsProvider tasNexus3RegistrySettingsProvider;

  @InjectMocks private TasRegistrySettingsAdapter settingsAdapter;

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsDockerHubPublic() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.DOCKER_HUB_PUBLIC).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasDockerHubPublicRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPrivateRegistrySettingsProvider, tasArtifactoryRegistrySettingsProvider,
        tasContainerRegistrySettingsProvider, tasElasticContainerRegistrySettingsProvider,
        tasGoogleContainerRegistrySettingsProvider, tasGoogleArtifactRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsDockerHubPrivate() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.DOCKER_HUB_PRIVATE).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasDockerHubPrivateRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasArtifactoryRegistrySettingsProvider,
        tasContainerRegistrySettingsProvider, tasElasticContainerRegistrySettingsProvider,
        tasGoogleContainerRegistrySettingsProvider, tasGoogleArtifactRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsECR() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.ECR).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasElasticContainerRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasDockerHubPrivateRegistrySettingsProvider,
        tasArtifactoryRegistrySettingsProvider, tasContainerRegistrySettingsProvider,
        tasGoogleContainerRegistrySettingsProvider, tasGoogleArtifactRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsArtifactory() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.ARTIFACTORY_PRIVATE_REGISTRY).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasArtifactoryRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasDockerHubPrivateRegistrySettingsProvider,
        tasContainerRegistrySettingsProvider, tasElasticContainerRegistrySettingsProvider,
        tasGoogleContainerRegistrySettingsProvider, tasGoogleArtifactRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsAcr() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.ACR).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasContainerRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasDockerHubPrivateRegistrySettingsProvider,
        tasArtifactoryRegistrySettingsProvider, tasElasticContainerRegistrySettingsProvider,
        tasGoogleContainerRegistrySettingsProvider, tasGoogleArtifactRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsGCR() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.GCR).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasGoogleContainerRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasDockerHubPrivateRegistrySettingsProvider,
        tasArtifactoryRegistrySettingsProvider, tasContainerRegistrySettingsProvider,
        tasElasticContainerRegistrySettingsProvider, tasGoogleArtifactRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsGAR() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.GAR).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasGoogleArtifactRegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasDockerHubPrivateRegistrySettingsProvider,
        tasArtifactoryRegistrySettingsProvider, tasContainerRegistrySettingsProvider,
        tasElasticContainerRegistrySettingsProvider, tasGoogleContainerRegistrySettingsProvider,
        tasNexus3RegistrySettingsProvider);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsNexusRegistry() {
    final TasContainerArtifactConfig artifactConfig =
        TasContainerArtifactConfig.builder().registryType(TasArtifactRegistryType.NEXUS_PRIVATE_REGISTRY).build();

    settingsAdapter.getContainerSettings(artifactConfig);

    verify(tasNexus3RegistrySettingsProvider).getContainerSettings(artifactConfig, decryptionHelper);
    verifyNoMoreInteractions(tasDockerHubPublicRegistrySettingsProvider, tasDockerHubPrivateRegistrySettingsProvider,
        tasArtifactoryRegistrySettingsProvider, tasContainerRegistrySettingsProvider,
        tasElasticContainerRegistrySettingsProvider, tasGoogleContainerRegistrySettingsProvider,
        tasGoogleArtifactRegistrySettingsProvider);
  }
}