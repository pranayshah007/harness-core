/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureArtifactsService;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.intfc.AzureArtifactsBuildService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureArtifactsBuildServiceTest extends WingsBaseTest {
  @Mock private AzureArtifactsService azureArtifactsService;
  @Inject @InjectMocks private AzureArtifactsBuildService azureArtifactsBuildService;
  @Inject private FeatureFlagService featureFlagService;

  private static final AzureArtifactsPATConfig azureArtifactsPATConfig =
      AzureArtifactsPATConfig.builder().azureDevopsUrl("http://dev.azure.com/ORG").pat("pat".toCharArray()).build();
  private static final AzureArtifactsArtifactStream azureArtifactsArtifactStream =
      AzureArtifactsArtifactStream.builder()
          .accountId(ACCOUNT_ID)
          .appId(APP_ID)
          .settingId(SETTING_ID)
          .autoPopulate(true)
          .serviceId(SERVICE_ID)
          .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
          .project(null)
          .feed("FEED")
          .packageId("PACKAGE_ID")
          .packageName("GROUP_ID:ARTIFACT_ID")
          .build();

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateArtifactServer() {
    when(azureArtifactsService.validateArtifactServer(azureArtifactsPATConfig, null, true)).thenReturn(true);
    assertThat(azureArtifactsBuildService.validateArtifactServer(azureArtifactsPATConfig, null)).isTrue();

    when(azureArtifactsService.validateArtifactServer(azureArtifactsPATConfig, null, true)).thenReturn(false);
    assertThat(azureArtifactsBuildService.validateArtifactServer(azureArtifactsPATConfig, null)).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldValidateArtifactSource() {
    ArtifactStreamAttributes artifactStreamAttributes =
        azureArtifactsArtifactStream.fetchArtifactStreamAttributes(featureFlagService);
    when(azureArtifactsService.validateArtifactSource(azureArtifactsPATConfig, null, artifactStreamAttributes))
        .thenReturn(true);
    assertThat(
        azureArtifactsBuildService.validateArtifactSource(azureArtifactsPATConfig, null, artifactStreamAttributes))
        .isTrue();

    when(azureArtifactsService.validateArtifactSource(azureArtifactsPATConfig, null, artifactStreamAttributes))
        .thenReturn(false);
    assertThat(
        azureArtifactsBuildService.validateArtifactSource(azureArtifactsPATConfig, null, artifactStreamAttributes))
        .isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    ArtifactStreamAttributes artifactStreamAttributes =
        azureArtifactsArtifactStream.fetchArtifactStreamAttributes(featureFlagService);
    when(azureArtifactsService.getBuilds(artifactStreamAttributes, azureArtifactsPATConfig, null))
        .thenReturn(
            Lists.newArrayList(aBuildDetails().withNumber("10").build(), aBuildDetails().withNumber("9").build()));
    List<BuildDetails> builds =
        azureArtifactsBuildService.getBuilds(APP_ID, artifactStreamAttributes, azureArtifactsPATConfig, null);
    assertThat(builds).hasSize(2).extracting(BuildDetails::getNumber).containsExactly("10", "9");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetProjects() {
    AzureDevopsProject project1 = new AzureDevopsProject();
    project1.setId("id1");
    project1.setName("name1");
    AzureDevopsProject project2 = new AzureDevopsProject();
    project2.setId("id2");
    project2.setName("name2");
    when(azureArtifactsService.listProjects(azureArtifactsPATConfig, null))
        .thenReturn(Lists.newArrayList(project1, project2));
    List<AzureDevopsProject> projects = azureArtifactsBuildService.getProjects(azureArtifactsPATConfig, null);
    assertThat(projects).hasSize(2).extracting(AzureDevopsProject::getId).containsExactly("id1", "id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetFeeds() {
    AzureArtifactsFeed feed1 = new AzureArtifactsFeed();
    feed1.setId("id1");
    feed1.setName("name1");
    AzureArtifactsFeed feed2 = new AzureArtifactsFeed();
    feed2.setId("id2");
    feed2.setName("name2");
    when(azureArtifactsService.listFeeds(azureArtifactsPATConfig, null, null))
        .thenReturn(Lists.newArrayList(feed1, feed2));
    List<AzureArtifactsFeed> feeds = azureArtifactsBuildService.getFeeds(azureArtifactsPATConfig, null, null);
    assertThat(feeds).hasSize(2).extracting(AzureArtifactsFeed::getId).containsExactly("id1", "id2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldGetPackages() {
    AzureArtifactsPackage package1 = new AzureArtifactsPackage();
    package1.setId("id1");
    package1.setName("name1");
    AzureArtifactsPackage package2 = new AzureArtifactsPackage();
    package2.setId("id2");
    package2.setName("name2");
    when(azureArtifactsService.listPackages(
             azureArtifactsPATConfig, null, null, "FEED", AzureArtifactsArtifactStreamProtocolType.maven.name()))
        .thenReturn(Lists.newArrayList(package1, package2));
    List<AzureArtifactsPackage> packages = azureArtifactsBuildService.getPackages(
        azureArtifactsPATConfig, null, null, "FEED", AzureArtifactsArtifactStreamProtocolType.maven.name());
    assertThat(packages).hasSize(2).extracting(AzureArtifactsPackage::getId).containsExactly("id1", "id2");
  }
}
