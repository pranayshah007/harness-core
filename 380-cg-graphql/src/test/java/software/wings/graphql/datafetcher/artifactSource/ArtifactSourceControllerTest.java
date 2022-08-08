/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifactSource;

import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.GCS;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.beans.artifact.ArtifactStreamType.SFTP;
import static software.wings.beans.artifact.ArtifactStreamType.SMB;
import static software.wings.graphql.datafetcher.artifactSource.ArtifactSourceTestHelper.getNexusArtifactStream;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.IMAGE_NAME;
import static software.wings.utils.WingsTestConstants.JOB_NAME;
import static software.wings.utils.WingsTestConstants.LONG_DEFAULT_VALUE;
import static software.wings.utils.WingsTestConstants.PROJECT_ID;
import static software.wings.utils.WingsTestConstants.REPO_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureMachineImageArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.GcsArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.artifact.SftpArtifactStream;
import software.wings.beans.artifact.SmbArtifactStream;
import software.wings.graphql.schema.type.QLAzureImageDefinition;
import software.wings.graphql.schema.type.QLKeyValuePair;
import software.wings.graphql.schema.type.artifactSource.QLACRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAMIArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAmazonS3ArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryDockerProps;
import software.wings.graphql.schema.type.artifactSource.QLArtifactoryFileProps;
import software.wings.graphql.schema.type.artifactSource.QLAzureArtifactsArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLAzureMachineImageArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLBambooArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLDockerArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLECRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLGCRArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLGCSArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLJenkinsArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLNexusDockerProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusMavenProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusNpmProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusNugetProps;
import software.wings.graphql.schema.type.artifactSource.QLNexusRepositoryFormat;
import software.wings.graphql.schema.type.artifactSource.QLSFTPArtifactSource;
import software.wings.graphql.schema.type.artifactSource.QLSMBArtifactSource;
import software.wings.utils.RepositoryFormat;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactSourceControllerTest extends WingsBaseTest {
  private static final String REGION = "REGION";
  private static final String REGISRTY_NAME = "REGISRTY_NAME";
  private static final List<String> artifactPaths = asList(ARTIFACT_PATH, ARTIFACT_PATH + "2");

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldReturnParameterizedNexus2ArtifactSource() {
    NexusArtifactStream nexusArtifactStream = getNexusArtifactStream(SETTING_ID, ARTIFACT_STREAM_ID);
    List<String> parameters = asList("repo", "groupId", "path");
    QLArtifactSource qlArtifactSource =
        ArtifactSourceController.populateArtifactSource(nexusArtifactStream, parameters);
    assertThat(qlArtifactSource).isNotNull();
    assertThat(qlArtifactSource).isInstanceOf(QLNexusArtifactSource.class);
    QLNexusArtifactSource qlNexusArtifactSource = (QLNexusArtifactSource) qlArtifactSource;
    assertThat(qlNexusArtifactSource.getName()).isEqualTo("testNexus");
    assertThat(qlNexusArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlNexusArtifactSource.getParameters().size()).isEqualTo(3);
    assertThat(qlNexusArtifactSource.getParameters()).containsAll(asList("repo", "groupId", "path"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateACRArtifactSource() {
    final String subcscription_id = "SUBCSCRIPTION_ID";
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .createdAt(LONG_DEFAULT_VALUE)
                                              .name(ARTIFACT_STREAM_NAME)
                                              .repositoryName(REPO_NAME)
                                              .settingId(SETTING_ID)
                                              .subscriptionId(subcscription_id)
                                              .registryName(REGISRTY_NAME)
                                              .build();

    acrArtifactStream.setArtifactStreamType(ACR.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(acrArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLACRArtifactSource.class);

    QLACRArtifactSource qlAcrArtifactSource = (QLACRArtifactSource) qlArtifactSource;

    assertThat(qlAcrArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlAcrArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlAcrArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlAcrArtifactSource.getRegistryName()).isEqualTo(REGISRTY_NAME);
    assertThat(qlAcrArtifactSource.getRepositoryName()).isEqualTo(REPO_NAME);
    assertThat(qlAcrArtifactSource.getSubscriptionId()).isEqualTo(subcscription_id);
    assertThat(qlAcrArtifactSource.getAzureCloudProviderId()).isEqualTo(SETTING_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateAMIArtifactSource() {
    AmiArtifactStream.Tag tag1 = new AmiArtifactStream.Tag();
    final String key1 = "key1";
    tag1.setKey(key1);
    final String value1 = "value1";
    tag1.setValue(value1);
    AmiArtifactStream.Tag tag2 = new AmiArtifactStream.Tag();
    final String key2 = "key2";
    tag2.setKey(key2);
    final String value2 = "value2";
    tag2.setValue(value2);
    List<AmiArtifactStream.Tag> tags = asList(tag1, tag2);
    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .createdAt(LONG_DEFAULT_VALUE)
                                              .name(ARTIFACT_STREAM_NAME)
                                              .region(REGION)
                                              .settingId(SETTING_ID)
                                              .tags(tags)
                                              .build();

    amiArtifactStream.setArtifactStreamType(AMI.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(amiArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLAMIArtifactSource.class);

    QLAMIArtifactSource qlAmiArtifactSource = (QLAMIArtifactSource) qlArtifactSource;

    assertThat(qlAmiArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlAmiArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlAmiArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlAmiArtifactSource.getRegion()).isEqualTo(REGION);
    assertThat(qlAmiArtifactSource.getAwsCloudProviderId()).isEqualTo(SETTING_ID);
    List<QLKeyValuePair> awsTags = qlAmiArtifactSource.getAwsTags();
    assertThat(awsTags.get(0).getKey()).isEqualTo(key1);
    assertThat(awsTags.get(0).getValue()).isEqualTo(value1);
    assertThat(awsTags.get(1).getKey()).isEqualTo(key2);
    assertThat(awsTags.get(1).getValue()).isEqualTo(value2);
    assertThat(qlAmiArtifactSource.getAmiResourceFilters()).isEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateECRArtifactSource() {
    EcrArtifactStream ecrArtifactStream = EcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .createdAt(LONG_DEFAULT_VALUE)
                                              .name(ARTIFACT_STREAM_NAME)
                                              .region(REGION)
                                              .settingId(SETTING_ID)
                                              .imageName(IMAGE_NAME)
                                              .build();

    ecrArtifactStream.setArtifactStreamType(ECR.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(ecrArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLECRArtifactSource.class);

    QLECRArtifactSource qlEcrArtifactSource = (QLECRArtifactSource) qlArtifactSource;

    assertThat(qlEcrArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlEcrArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlEcrArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlEcrArtifactSource.getRegion()).isEqualTo(REGION);
    assertThat(qlEcrArtifactSource.getAwsCloudProviderId()).isEqualTo(SETTING_ID);
    assertThat(qlEcrArtifactSource.getImageName()).isEqualTo(IMAGE_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateGCRArtifactSource() {
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .createdAt(LONG_DEFAULT_VALUE)
                                              .name(ARTIFACT_STREAM_NAME)
                                              .dockerImageName(IMAGE_NAME)
                                              .settingId(SETTING_ID)
                                              .registryHostName(REGISRTY_NAME)
                                              .build();

    gcrArtifactStream.setArtifactStreamType(GCR.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(gcrArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLGCRArtifactSource.class);

    QLGCRArtifactSource qlGcrArtifactSource = (QLGCRArtifactSource) qlArtifactSource;

    assertThat(qlGcrArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlGcrArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlGcrArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlGcrArtifactSource.getRegistryHostName()).isEqualTo(REGISRTY_NAME);
    assertThat(qlGcrArtifactSource.getGcpCloudProviderId()).isEqualTo(SETTING_ID);
    assertThat(qlGcrArtifactSource.getDockerImageName()).isEqualTo(IMAGE_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateGCSArtifactSource() {
    GcsArtifactStream gcsArtifactStream = GcsArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .createdAt(LONG_DEFAULT_VALUE)
                                              .name(ARTIFACT_STREAM_NAME)
                                              .artifactPaths(artifactPaths)
                                              .settingId(SETTING_ID)
                                              .jobname(JOB_NAME)
                                              .projectId(PROJECT_ID)
                                              .build();

    gcsArtifactStream.setArtifactStreamType(GCS.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(gcsArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLGCSArtifactSource.class);

    QLGCSArtifactSource qlGcsArtifactSource = (QLGCSArtifactSource) qlArtifactSource;

    assertThat(qlGcsArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlGcsArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlGcsArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlGcsArtifactSource.getArtifactPaths()).isEqualTo(artifactPaths);
    assertThat(qlGcsArtifactSource.getGcpCloudProviderId()).isEqualTo(SETTING_ID);
    assertThat(qlGcsArtifactSource.getBucket()).isEqualTo(JOB_NAME);
    assertThat(qlGcsArtifactSource.getProjectId()).isEqualTo(PROJECT_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateSMBArtifactSource() {
    SmbArtifactStream smbArtifactStream = SmbArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .createdAt(LONG_DEFAULT_VALUE)
                                              .name(ARTIFACT_STREAM_NAME)
                                              .artifactPaths(artifactPaths)
                                              .settingId(SETTING_ID)
                                              .build();

    smbArtifactStream.setArtifactStreamType(SMB.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(smbArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLSMBArtifactSource.class);

    QLSMBArtifactSource qlSmbArtifactSource = (QLSMBArtifactSource) qlArtifactSource;

    assertThat(qlSmbArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlSmbArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlSmbArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlSmbArtifactSource.getSmbConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlSmbArtifactSource.getArtifactPaths()).isEqualTo(artifactPaths);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateSFTPArtifactSource() {
    SftpArtifactStream sftpArtifactStream = SftpArtifactStream.builder()
                                                .uuid(ARTIFACT_STREAM_ID)
                                                .createdAt(LONG_DEFAULT_VALUE)
                                                .name(ARTIFACT_STREAM_NAME)
                                                .artifactPaths(artifactPaths)
                                                .settingId(SETTING_ID)
                                                .build();

    sftpArtifactStream.setArtifactStreamType(SFTP.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(sftpArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLSFTPArtifactSource.class);

    QLSFTPArtifactSource qlSftpArtifactSource = (QLSFTPArtifactSource) qlArtifactSource;

    assertThat(qlSftpArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlSftpArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlSftpArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlSftpArtifactSource.getSftpConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlSftpArtifactSource.getArtifactPaths()).isEqualTo(artifactPaths);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateNexusArtifactSource() {
    final String CLASSIFIER = "CLASSIFIER";
    final String EXTENSION = "EXTENSION";
    final String GROUP_ID = "GROUP_ID";
    final String DOCKER_URL = "DOCKER_URL";
    final String package_name = "PACKAGE_NAME";
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .createdAt(LONG_DEFAULT_VALUE)
                                                  .name(ARTIFACT_STREAM_NAME)
                                                  .imageName(IMAGE_NAME)
                                                  .settingId(SETTING_ID)
                                                  .repositoryFormat(RepositoryFormat.docker.name())
                                                  .dockerRegistryUrl(DOCKER_URL)
                                                  .jobname(JOB_NAME)
                                                  .build();

    nexusArtifactStream.setArtifactStreamType(NEXUS.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(nexusArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLNexusArtifactSource.class);

    QLNexusArtifactSource qlNexusArtifactSource = (QLNexusArtifactSource) qlArtifactSource;

    assertThat(qlNexusArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlNexusArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlNexusArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlNexusArtifactSource.getProperties()).isInstanceOf(QLNexusDockerProps.class);

    QLNexusDockerProps qlNexusDockerProps = (QLNexusDockerProps) qlNexusArtifactSource.getProperties();
    assertThat(qlNexusDockerProps.getNexusConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlNexusDockerProps.getRepository()).isEqualTo(JOB_NAME);
    assertThat(qlNexusDockerProps.getDockerImageName()).isEqualTo(IMAGE_NAME);
    assertThat(qlNexusDockerProps.getDockerRegistryUrl()).isEqualTo(DOCKER_URL);
    assertThat(qlNexusDockerProps.getRepositoryFormat()).isEqualTo(QLNexusRepositoryFormat.DOCKER);

    nexusArtifactStream.setRepositoryFormat(RepositoryFormat.maven.name());
    nexusArtifactStream.setGroupId(GROUP_ID);
    nexusArtifactStream.setArtifactPaths(artifactPaths);
    nexusArtifactStream.setClassifier(CLASSIFIER);
    nexusArtifactStream.setExtension(EXTENSION);

    qlArtifactSource = ArtifactSourceController.populateArtifactSource(nexusArtifactStream);
    qlNexusArtifactSource = (QLNexusArtifactSource) qlArtifactSource;
    assertThat(qlNexusArtifactSource.getProperties()).isInstanceOf(QLNexusMavenProps.class);
    QLNexusMavenProps qlNexusMavenProps = (QLNexusMavenProps) qlNexusArtifactSource.getProperties();
    assertThat(qlNexusMavenProps.getNexusConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlNexusMavenProps.getRepository()).isEqualTo(JOB_NAME);
    assertThat(qlNexusMavenProps.getArtifactId()).isEqualTo(artifactPaths);
    assertThat(qlNexusMavenProps.getClassifier()).isEqualTo(CLASSIFIER);
    assertThat(qlNexusMavenProps.getGroupId()).isEqualTo(GROUP_ID);
    assertThat(qlNexusMavenProps.getExtension()).isEqualTo(EXTENSION);

    nexusArtifactStream.setRepositoryFormat(RepositoryFormat.npm.name());
    nexusArtifactStream.setPackageName(package_name);

    qlArtifactSource = ArtifactSourceController.populateArtifactSource(nexusArtifactStream);
    qlNexusArtifactSource = (QLNexusArtifactSource) qlArtifactSource;
    assertThat(qlNexusArtifactSource.getProperties()).isInstanceOf(QLNexusNpmProps.class);
    QLNexusNpmProps qlNexusNpmProps = (QLNexusNpmProps) qlNexusArtifactSource.getProperties();
    assertThat(qlNexusNpmProps.getNexusConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlNexusNpmProps.getRepository()).isEqualTo(JOB_NAME);
    assertThat(qlNexusNpmProps.getPackageName()).isEqualTo(package_name);

    nexusArtifactStream.setRepositoryFormat(RepositoryFormat.nuget.name());

    qlArtifactSource = ArtifactSourceController.populateArtifactSource(nexusArtifactStream);
    qlNexusArtifactSource = (QLNexusArtifactSource) qlArtifactSource;
    assertThat(qlNexusArtifactSource.getProperties()).isInstanceOf(QLNexusNugetProps.class);
    QLNexusNugetProps qlNexusNugetProps = (QLNexusNugetProps) qlNexusArtifactSource.getProperties();
    assertThat(qlNexusNugetProps.getNexusConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlNexusNugetProps.getRepository()).isEqualTo(JOB_NAME);
    assertThat(qlNexusNugetProps.getPackageName()).isEqualTo(package_name);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateBambooArtifactSource() {
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .createdAt(LONG_DEFAULT_VALUE)
                                                    .name(ARTIFACT_STREAM_NAME)
                                                    .artifactPaths(artifactPaths)
                                                    .settingId(SETTING_ID)
                                                    .jobname(JOB_NAME)
                                                    .build();

    bambooArtifactStream.setArtifactStreamType(BAMBOO.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(bambooArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLBambooArtifactSource.class);

    QLBambooArtifactSource qlBambooArtifactSource = (QLBambooArtifactSource) qlArtifactSource;

    assertThat(qlBambooArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlBambooArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlBambooArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlBambooArtifactSource.getBambooConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlBambooArtifactSource.getArtifactPaths()).isEqualTo(artifactPaths);
    assertThat(qlBambooArtifactSource.getPlanKey()).isEqualTo(JOB_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateDockerArtifactSource() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .createdAt(LONG_DEFAULT_VALUE)
                                                    .name(ARTIFACT_STREAM_NAME)
                                                    .settingId(SETTING_ID)
                                                    .imageName(IMAGE_NAME)
                                                    .build();

    dockerArtifactStream.setArtifactStreamType(DOCKER.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(dockerArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLDockerArtifactSource.class);

    QLDockerArtifactSource qldockerArtifactSource = (QLDockerArtifactSource) qlArtifactSource;

    assertThat(qldockerArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qldockerArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qldockerArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qldockerArtifactSource.getDockerConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qldockerArtifactSource.getImageName()).isEqualTo(IMAGE_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateJenkinsArtifactSource() {
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .uuid(ARTIFACT_STREAM_ID)
                                                      .createdAt(LONG_DEFAULT_VALUE)
                                                      .name(ARTIFACT_STREAM_NAME)
                                                      .artifactPaths(artifactPaths)
                                                      .settingId(SETTING_ID)
                                                      .jobname(JOB_NAME)
                                                      .build();

    jenkinsArtifactStream.setArtifactStreamType(JENKINS.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(jenkinsArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLJenkinsArtifactSource.class);

    QLJenkinsArtifactSource qlJenkinsArtifactSource = (QLJenkinsArtifactSource) qlArtifactSource;

    assertThat(qlJenkinsArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlJenkinsArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlJenkinsArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlJenkinsArtifactSource.getJenkinsConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlJenkinsArtifactSource.getArtifactPaths()).isEqualTo(artifactPaths);
    assertThat(qlJenkinsArtifactSource.getJobName()).isEqualTo(JOB_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateAs3ArtifactSource() {
    final String REGISRTY_NAME = "REGISRTY_NAME";
    final String subcscription_id = "SUBCSCRIPTION_ID";
    AmazonS3ArtifactStream as3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                   .uuid(ARTIFACT_STREAM_ID)
                                                   .createdAt(LONG_DEFAULT_VALUE)
                                                   .name(ARTIFACT_STREAM_NAME)
                                                   .jobname(JOB_NAME)
                                                   .settingId(SETTING_ID)
                                                   .artifactPaths(artifactPaths)
                                                   .build();

    as3ArtifactStream.setArtifactStreamType(AMAZON_S3.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(as3ArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLAmazonS3ArtifactSource.class);

    QLAmazonS3ArtifactSource qlAcrArtifactSource = (QLAmazonS3ArtifactSource) qlArtifactSource;

    assertThat(qlAcrArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlAcrArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlAcrArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlAcrArtifactSource.getAwsCloudProviderId()).isEqualTo(SETTING_ID);
    assertThat(qlAcrArtifactSource.getBucket()).isEqualTo(JOB_NAME);
    assertThat(qlAcrArtifactSource.getArtifactPaths()).isEqualTo(artifactPaths);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateArtifactoryArtifactSource() {
    final String DOCKER_URL = "DOCKER_URL";
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .uuid(ARTIFACT_STREAM_ID)
                                                              .createdAt(LONG_DEFAULT_VALUE)
                                                              .name(ARTIFACT_STREAM_NAME)
                                                              .dockerRepositoryServer(DOCKER_URL)
                                                              .settingId(SETTING_ID)
                                                              .imageName(IMAGE_NAME)
                                                              .jobname(JOB_NAME)
                                                              .build();

    artifactoryArtifactStream.setArtifactStreamType(ARTIFACTORY.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(artifactoryArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLArtifactoryArtifactSource.class);

    QLArtifactoryArtifactSource qlArtifactoryArtifactSource = (QLArtifactoryArtifactSource) qlArtifactSource;

    assertThat(qlArtifactoryArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlArtifactoryArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlArtifactoryArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);

    assertThat(qlArtifactoryArtifactSource.getProperties()).isInstanceOf(QLArtifactoryDockerProps.class);
    QLArtifactoryDockerProps dockerProps = (QLArtifactoryDockerProps) qlArtifactoryArtifactSource.getProperties();
    assertThat(dockerProps.getDockerImageName()).isEqualTo(IMAGE_NAME);
    assertThat(dockerProps.getDockerRepositoryServer()).isEqualTo(DOCKER_URL);
    assertThat(dockerProps.getArtifactoryConnectorId()).isEqualTo(SETTING_ID);
    assertThat(dockerProps.getRepository()).isEqualTo(JOB_NAME);

    artifactoryArtifactStream.setImageName(null);
    artifactoryArtifactStream.setArtifactPattern(artifactPaths.get(0));

    QLArtifactSource qlArtifactSource2 = ArtifactSourceController.populateArtifactSource(artifactoryArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLArtifactoryArtifactSource.class);

    QLArtifactoryArtifactSource qlArtifactoryArtifactSource2 = (QLArtifactoryArtifactSource) qlArtifactSource2;

    assertThat(qlArtifactoryArtifactSource2.getProperties()).isInstanceOf(QLArtifactoryFileProps.class);
    QLArtifactoryFileProps fileProps = (QLArtifactoryFileProps) qlArtifactoryArtifactSource2.getProperties();
    assertThat(fileProps.getArtifactPath()).isEqualTo(artifactPaths.get(0));
    assertThat(fileProps.getArtifactoryConnectorId()).isEqualTo(SETTING_ID);
    assertThat(dockerProps.getRepository()).isEqualTo(JOB_NAME);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateAzureArtifactSource() {
    final String PACKAGE_NAME = "PACKAGE_NAME";
    final String FEED_NAME = "FEED_NAME";
    final String PACKAGE_TYPE = "PACKAGE_TYPE";
    AzureArtifactsArtifactStream azureArtifactStream = AzureArtifactsArtifactStream.builder()
                                                           .uuid(ARTIFACT_STREAM_ID)
                                                           .createdAt(LONG_DEFAULT_VALUE)
                                                           .name(ARTIFACT_STREAM_NAME)
                                                           .packageName(PACKAGE_NAME)
                                                           .settingId(SETTING_ID)
                                                           .protocolType(PACKAGE_TYPE)
                                                           .project(PROJECT_ID)
                                                           .feed(FEED_NAME)
                                                           .build();

    azureArtifactStream.setArtifactStreamType(AZURE_ARTIFACTS.name());

    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(azureArtifactStream);
    assertThat(qlArtifactSource).isInstanceOf(QLAzureArtifactsArtifactSource.class);

    QLAzureArtifactsArtifactSource qlAzureArtifactSource = (QLAzureArtifactsArtifactSource) qlArtifactSource;

    assertThat(qlAzureArtifactSource.getCreatedAt()).isEqualTo(LONG_DEFAULT_VALUE);
    assertThat(qlAzureArtifactSource.getId()).isEqualTo(ARTIFACT_STREAM_ID);
    assertThat(qlAzureArtifactSource.getName()).isEqualTo(ARTIFACT_STREAM_NAME);
    assertThat(qlAzureArtifactSource.getAzureConnectorId()).isEqualTo(SETTING_ID);
    assertThat(qlAzureArtifactSource.getPackageName()).isEqualTo(PACKAGE_NAME);
    assertThat(qlAzureArtifactSource.getFeedName()).isEqualTo(FEED_NAME);
    assertThat(qlAzureArtifactSource.getProject()).isEqualTo(PROJECT_ID);
    assertThat(qlAzureArtifactSource.getPackageType()).isEqualTo(PACKAGE_TYPE);
    assertThat(qlAzureArtifactSource.getScope()).isEqualTo("PROJECT");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldPopulateAzureMachineImageArtifactSource() {
    AzureMachineImageArtifactStream stream =
        AzureMachineImageArtifactStream.builder()
            .uuid(ARTIFACT_ID)
            .name(ARTIFACT_STREAM_NAME)
            .settingId(SETTING_ID)
            .createdAt(LONG_DEFAULT_VALUE)
            .subscriptionId("subID")
            .imageType(IMAGE_GALLERY)
            .imageDefinition(AzureMachineImageArtifactStream.ImageDefinition.builder()
                                 .resourceGroup("resourceGroup")
                                 .imageDefinitionName("imageDefinition")
                                 .imageGalleryName("imageGallery")
                                 .build())
            .build();
    QLArtifactSource qlArtifactSource = ArtifactSourceController.populateArtifactSource(stream);
    assertThat(qlArtifactSource).isInstanceOf(QLAzureMachineImageArtifactSource.class);

    QLAzureMachineImageArtifactSource qaArtifactSource = (QLAzureMachineImageArtifactSource) qlArtifactSource;
    QLAzureMachineImageArtifactSource expected = QLAzureMachineImageArtifactSource.builder()
                                                     .id(ARTIFACT_ID)
                                                     .name(ARTIFACT_STREAM_NAME)
                                                     .createdAt(LONG_DEFAULT_VALUE)
                                                     .azureCloudProviderId(SETTING_ID)
                                                     .imageType("IMAGE_GALLERY")
                                                     .subscriptionId("subID")
                                                     .imageDefinition(QLAzureImageDefinition.builder()
                                                                          .resourceGroup("resourceGroup")
                                                                          .imageGalleryName("imageGallery")
                                                                          .imageDefinitionName("imageDefinition")
                                                                          .build())
                                                     .build();
    assertEquals("GraphQL artifact source should be mapped correctly", expected, qaArtifactSource);
  }
}
