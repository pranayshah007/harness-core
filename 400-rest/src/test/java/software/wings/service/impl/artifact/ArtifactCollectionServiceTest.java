/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.ArtifactType.DOCKER;
import static software.wings.utils.ArtifactType.RPM;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACTORY_URL;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.IMAGE_NAME;
import static software.wings.utils.WingsTestConstants.JOB_NAME;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.REGISTRY_HOST;
import static software.wings.utils.WingsTestConstants.REPO_NAME;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.artifact.SmbArtifactStream;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.template.artifactsource.CustomRepositoryMapping;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceRequestType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsEcrHelperServiceManager;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.ArtifactType;
import software.wings.utils.DelegateArtifactCollectionUtils;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDC)
public class ArtifactCollectionServiceTest extends WingsBaseTest {
  public static final String LATEST_BUILD_NUMBER = "latest";
  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";
  @Inject @Spy private HPersistence persistence;
  @InjectMocks
  @Inject
  @Named("AsyncArtifactCollectionService")
  private ArtifactCollectionService artifactCollectionService;
  @InjectMocks @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @InjectMocks @Inject private DelegateArtifactCollectionUtils delegateArtifactCollectionUtils;

  @Mock ArtifactStreamService artifactStreamService;
  @Mock private ArtifactService artifactService;
  @Mock private BuildSourceService buildSourceService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private AwsEcrHelperServiceManager awsEcrHelperServiceManager;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private Query query;
  @Mock private MorphiaIterator<Artifact, Artifact> artifactIterator;

  private Artifact.Builder artifactBuilder = anArtifact()
                                                 .withAppId(APP_ID)
                                                 .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                 .withRevision("1.0")
                                                 .withDisplayName("DISPLAY_NAME")
                                                 .withCreatedAt(System.currentTimeMillis())
                                                 .withCreatedBy(EmbeddedUser.builder().uuid("USER_ID").build());

  private Artifact artifact = artifactBuilder.build();

  private SettingAttribute settingAttribute = aSettingAttribute().withAccountId(ACCOUNT_ID).build();

  @Before
  public void setUp() {
    when(persistence.get(Artifact.class, ARTIFACT_ID)).thenReturn(artifact);
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectJenkinsArtifact() {
    BuildDetails jenkinsBuildDetails = getJenkinsBuildDetails();
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    Artifact newArtifact = artifactCollectionUtils.getArtifact(jenkinsArtifactStream, jenkinsBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);

    Artifact collectedArtifact = artifactCollectionService.collectArtifact(ARTIFACT_STREAM_ID, jenkinsBuildDetails);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo("3594");
    assertThat(collectedArtifact.getUrl()).isEqualTo("https://jenkins.harness.io/job/portal/3594/");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectJenkinsArtifactConnectorLevel() {
    BuildDetails jenkinsBuildDetails = getJenkinsBuildDetails();
    JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                      .uuid(ARTIFACT_STREAM_ID)
                                                      .appId(GLOBAL_APP_ID)
                                                      .sourceName("ARTIFACT_SOURCE")
                                                      .settingId(SETTING_ID)
                                                      .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);
    Artifact newArtifact = artifactCollectionUtils.getArtifact(jenkinsArtifactStream, jenkinsBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);

    Artifact collectedArtifact = artifactCollectionService.collectArtifact(ARTIFACT_STREAM_ID, jenkinsBuildDetails);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo("3594");
    assertThat(collectedArtifact.getUrl()).isEqualTo("https://jenkins.harness.io/job/portal/3594/");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectS3Artifact() {
    BuildDetails s3BuildDetails = getS3BuildDetails();

    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .uuid(ARTIFACT_STREAM_ID)
                                                        .appId(APP_ID)
                                                        .sourceName("ARTIFACT_SOURCE")
                                                        .serviceId(SERVICE_ID)
                                                        .settingId(SETTING_ID)
                                                        .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(amazonS3ArtifactStream);
    Artifact newArtifact = artifactCollectionUtils.getArtifact(amazonS3ArtifactStream, s3BuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);

    Artifact collectedArtifact = artifactCollectionService.collectArtifact(ARTIFACT_STREAM_ID, s3BuildDetails);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo("appstack/apache-tomcat-8.5.15.tar.gz");
    assertThat(collectedArtifact.getUrl())
        .isEqualTo("https://s3.amazonaws.com/harness-catalogs/appstack/apache-tomcat-8.5.15.tar.gz");
  }

  private BuildDetails getS3BuildDetails() {
    Map<String, String> map = new HashMap<>();
    map.put(WingsTestConstants.URL, "https://s3.amazonaws.com/harness-catalogs/appstack/apache-tomcat-8.5.15.tar.gz");
    map.put(ArtifactMetadataKeys.buildNo, "appstack/apache-tomcat-8.5.15.tar.gz");
    map.put(ArtifactMetadataKeys.bucketName, "harness-catalogs");
    map.put(ArtifactMetadataKeys.artifactPath, "appstack/apache-tomcat-8.5.15.tar.gz");
    map.put(ArtifactMetadataKeys.key, "appstack/apache-tomcat-8.5.15.tar.gz");

    return aBuildDetails().withNumber("appstack/apache-tomcat-8.5.15.tar.gz").withBuildParameters(map).build();
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsDocker() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .build();

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(dockerArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(dockerArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, dockerArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsEcr() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    EcrArtifactStream ecrArtifactStream = EcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .build();

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(ecrArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(ecrArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, ecrArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsGcr() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(gcrArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(gcrArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, gcrArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsAcr() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(acrArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(acrArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, acrArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsAmi() {
    String buildNumber = "AMI-Image";
    BuildDetails amiBuildDetails = aBuildDetails().withNumber(buildNumber).withRevision("ImageId").build();
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(dockerArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(dockerArtifactStream, amiBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(amiBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, dockerArtifactStream, buildNumber);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(buildNumber);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsNexusDocker() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .appId(APP_ID)
                                                  .sourceName("ARTIFACT_SOURCE")
                                                  .serviceId(SERVICE_ID)
                                                  .settingId(SETTING_ID)
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(nexusArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, nexusArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsNexus() {
    String buildNUmber = "1.1";
    BuildDetails dockerBuildDetails =
        aBuildDetails().withNumber(buildNUmber).withRevision(buildNUmber).withBuildUrl("buildUrl").build();
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .appId(APP_ID)
                                                  .sourceName("ARTIFACT_SOURCE")
                                                  .serviceId(SERVICE_ID)
                                                  .settingId(SETTING_ID)
                                                  .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(nexusArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, nexusArtifactStream, buildNUmber);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(buildNUmber);
    assertThat(collectedArtifact.getRevision()).isEqualTo(buildNUmber);
    assertThat(collectedArtifact.getUrl()).isEqualTo("buildUrl");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsArtifactoryDocker() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    ArtifactoryArtifactStream artifactoryArtifactStream = getArtifactoryArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactoryArtifactStream);

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(DOCKER).build());

    Artifact newArtifact = artifactCollectionUtils.getArtifact(artifactoryArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, artifactoryArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsArtifactoryGeneric() {
    String buildNumber = "todolist.rpm";
    BuildDetails artifactoryBuilds = aBuildDetails()
                                         .withNumber(buildNumber)
                                         .withArtifactPath("harness-rpm/todolist.rpm")
                                         .withArtifactFileSize("1222")
                                         .build();
    ArtifactoryArtifactStream artifactoryArtifactStream = getArtifactoryArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactoryArtifactStream);

    when(serviceResourceService.get(APP_ID, SERVICE_ID, false))
        .thenReturn(Service.builder().uuid(SERVICE_ID).artifactType(RPM).build());

    Artifact newArtifact = artifactCollectionUtils.getArtifact(artifactoryArtifactStream, artifactoryBuilds);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(artifactoryBuilds));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, artifactoryArtifactStream, buildNumber);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(buildNumber);
    assertThat(collectedArtifact.getArtifactPath()).isEqualTo("harness-rpm/todolist.rpm");
    assertThat(collectedArtifact.getArtifactFileSize()).isEqualTo(1222L);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsS3() {
    BuildDetails s3BuildDetails = getS3BuildDetails();
    String buildNumber = s3BuildDetails.getNumber();
    AmazonS3ArtifactStream s3ArtifactStream = getS3ArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(s3ArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(s3ArtifactStream, s3BuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(s3BuildDetails));

    Artifact collectedArtifact = artifactCollectionService.collectNewArtifacts(APP_ID, s3ArtifactStream, buildNumber);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(buildNumber);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsJenkins() {
    BuildDetails jenkinsBuildDetails = getJenkinsBuildDetails();
    String buildNUmber = jenkinsBuildDetails.getNumber();
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(jenkinsArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(jenkinsArtifactStream, jenkinsBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(jenkinsBuildDetails));

    when(buildSourceService.getLastSuccessfulBuild(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID))
        .thenReturn(jenkinsBuildDetails);

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, jenkinsArtifactStream, buildNUmber);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(buildNUmber);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsBamboo() {
    String buildNumber = "20";
    BuildDetails bambooBuildDetails = aBuildDetails().withNumber(buildNumber).build();
    BambooArtifactStream bambooArtifactStream = BambooArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(bambooArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(bambooArtifactStream, bambooBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(bambooBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, bambooArtifactStream, buildNumber);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(buildNumber);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldCollectSmbArtifact() {
    BuildDetails smbBuildDetails = getSmbBuildDetails();
    SmbArtifactStream smbArtifactStream = getSmbArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(smbArtifactStream);
    Artifact newArtifact = artifactCollectionUtils.getArtifact(smbArtifactStream, smbBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);

    Artifact collectedArtifact = artifactCollectionService.collectArtifact(ARTIFACT_STREAM_ID, smbBuildDetails);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo("todolist");
    assertThat(collectedArtifact.getUrl()).isEqualTo("smb:\\\\buildsrv.eastus.cloudapp.azure.com\\builds");
    assertThat(collectedArtifact.getBuildFullDisplayName()).isEqualTo("fullname");
    assertThat(collectedArtifact.getDisplayName()).isEqualTo("displayName");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForDockerWithCredentials() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("buildNo", "latest");
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .withMetadata(new ArtifactMetadata(metadata))
                            .build();
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName(IMAGE_NAME)
                                                    .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(dockerArtifactStream);
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withValue(DockerConfig.builder()
                                                           .dockerRegistryUrl("https://index.docker.io/v2/")
                                                           .username("some username")
                                                           .password("some password".toCharArray())
                                                           .build())
                                            .withAccountId(ACCOUNT_ID)
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo(IMAGE_NAME);
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("https://index.docker.io/v2/");
    assertThat(imageDetails.getUsername()).isEqualTo("some username");
    assertThat(imageDetails.getPassword()).isEqualTo("some password");
    assertThat(imageDetails.getDomainName()).isEqualTo("index.docker.io");
    assertThat(imageDetails.getTag()).isEqualTo("latest");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForDockerWithoutCredentials() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .imageName(IMAGE_NAME)
                                                    .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(dockerArtifactStream);
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(DockerConfig.builder().dockerRegistryUrl("https://index.docker.io/v2/").build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo(IMAGE_NAME);
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("https://index.docker.io/v2/");
    assertThat(imageDetails.getDomainName()).isEqualTo("index.docker.io");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForEcr() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    EcrArtifactStream ecrArtifactStream = EcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(ecrArtifactStream);
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(null);
    when(awsEcrHelperServiceManager.getEcrImageUrl(any(), any(), any(), any(), any())).thenReturn("ecrurl.com");
    when(awsEcrHelperServiceManager.getAmazonEcrAuthToken(any(), any(), any(), any(), any())).thenReturn("auth_token");
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo("ecrurl.com");
    assertThat(imageDetails.getSourceName()).isEqualTo("ARTIFACT_SOURCE");
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("https://ecrurl.com/");
    assertThat(imageDetails.getUsername()).isEqualTo("AWS");
    assertThat(imageDetails.getPassword()).isEqualTo("auth_token");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForAcr() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .registryHostName(REGISTRY_HOST)
                                              .repositoryName(REPO_NAME)
                                              .build();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(AzureConfig.builder().clientId("ClientId").tenantId("tenantId").key("key".toCharArray()).build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(acrArtifactStream);
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo(REGISTRY_HOST + '/' + REPO_NAME);
    assertThat(imageDetails.getSourceName()).isEqualTo(REPO_NAME);
    assertThat(imageDetails.getUsername()).isEqualTo("ClientId");
    assertThat(imageDetails.getPassword()).isEqualTo("key");
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("https://" + REGISTRY_HOST + "/");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForArtifactoryDocker() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .uuid(ARTIFACT_STREAM_ID)
                                                              .appId(APP_ID)
                                                              .sourceName("ARTIFACT_SOURCE")
                                                              .serviceId(SERVICE_ID)
                                                              .settingId(SETTING_ID)
                                                              .dockerRepositoryServer("server")
                                                              .jobname("harness-docker")
                                                              .imageName("busybox")
                                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withValue(ArtifactoryConfig.builder()
                                                           .artifactoryUrl(ARTIFACTORY_URL)
                                                           .username(USER_NAME)
                                                           .password(PASSWORD)
                                                           .build())
                                            .withAccountId(ACCOUNT_ID)
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactoryArtifactStream);
    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo("server/busybox");
    assertThat(imageDetails.getSourceName()).isEqualTo("ARTIFACT_SOURCE");
    assertThat(imageDetails.getUsername()).isEqualTo(USER_NAME);
    assertThat(imageDetails.getPassword()).isEqualTo(new String(PASSWORD));
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("http://server");

    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withValue(ArtifactoryConfig.builder().artifactoryUrl(ARTIFACTORY_URL).build())
                           .withAccountId(ACCOUNT_ID)
                           .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo("server/busybox");
    assertThat(imageDetails.getSourceName()).isEqualTo("ARTIFACT_SOURCE");
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("http://server");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForNexus() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                  .uuid(ARTIFACT_STREAM_ID)
                                                  .appId(APP_ID)
                                                  .sourceName("ARTIFACT_SOURCE")
                                                  .serviceId(SERVICE_ID)
                                                  .settingId(SETTING_ID)
                                                  .imageName("busybox")
                                                  .dockerPort("8080")
                                                  .dockerRegistryUrl("nexusUrl")
                                                  .build();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(NexusConfig.builder().nexusUrl("NEXUS_URL").username(USER_NAME).password(PASSWORD).build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(nexusArtifactStream);
    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo("nexusUrl/busybox");
    assertThat(imageDetails.getSourceName()).isEqualTo("ARTIFACT_SOURCE");
    assertThat(imageDetails.getUsername()).isEqualTo(USER_NAME);
    assertThat(imageDetails.getPassword()).isEqualTo(new String(PASSWORD));
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("http://nexusUrl");

    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withValue(NexusConfig.builder().nexusUrl("nexusUrl").build())
                           .withAccountId(ACCOUNT_ID)
                           .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo("nexusUrl/busybox");
    assertThat(imageDetails.getSourceName()).isEqualTo("ARTIFACT_SOURCE");
    assertThat(imageDetails.getRegistryUrl()).isEqualTo("http://nexusUrl");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForGcr() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .registryHostName(REGISTRY_HOST)
                                              .dockerImageName(IMAGE_NAME)
                                              .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(gcrArtifactStream);

    String serviceAccountFileContent = "{ \"key\": \"value\" }";
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(GcpConfig.builder()
                           .accountId("GCP_ACCOUNT_ID")
                           .serviceAccountKeyFileContent(serviceAccountFileContent.toCharArray())
                           .build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);

    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getName()).isEqualTo(REGISTRY_HOST + '/' + IMAGE_NAME);
    assertThat(imageDetails.getSourceName()).isEqualTo(REGISTRY_HOST + '/' + IMAGE_NAME);
    assertThat(imageDetails.getRegistryUrl()).isEqualTo(REGISTRY_HOST + '/' + IMAGE_NAME);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForCustom() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    CustomArtifactStream customArtifactStream = CustomArtifactStream.builder().sourceName(ARTIFACT_SOURCE_NAME).build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);
    ImageDetails imageDetails = artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
    assertThat(imageDetails).isNotNull();
    assertThat(imageDetails.getSourceName()).isEqualTo(ARTIFACT_SOURCE_NAME);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFetchContainerImageDetailsForUnsupportedType() {
    Artifact artifact = Artifact.Builder.anArtifact()
                            .withUuid(ARTIFACT_ID)
                            .withArtifactStreamId(ARTIFACT_STREAM_ID)
                            .withAppId(APP_ID)
                            .withSettingId(SETTING_ID)
                            .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                            .withRevision("1.0")
                            .build();
    AmazonS3ArtifactStream s3ArtifactStream = getS3ArtifactStream();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(s3ArtifactStream);
    artifactCollectionUtils.fetchContainerImageDetails(artifact, WORKFLOW_EXECUTION_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetBuildSourceParametersForJenkins() {
    // multi-artifact flag off
    JenkinsArtifactStream jenkinsArtifactStream = getJenkinsArtifactStream();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(JenkinsConfig.builder().jenkinsUrl("jenkinsurl").username(USER_NAME).password(PASSWORD).build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(null);
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().artifactType(ArtifactType.JAR).build());
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.limit(anyInt())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    Map<String, String> map = new HashMap<>();
    map.put("buildNo", "10");
    when(artifactIterator.next()).thenReturn(anArtifact().withMetadata(new ArtifactMetadata(map)).build());
    BuildSourceParameters buildSourceParameters =
        artifactCollectionUtils.getBuildSourceParameters(jenkinsArtifactStream, settingAttribute, true, true);
    assertThat(buildSourceParameters).isNotNull();
    assertThat(buildSourceParameters.getBuildSourceRequestType())
        .isEqualTo(BuildSourceRequestType.GET_LAST_SUCCESSFUL_BUILD);
    assertThat(buildSourceParameters.getLimit()).isEqualTo(-1);
    assertThat(buildSourceParameters.getSavedBuildDetailsKeys()).contains("10");

    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(anArtifact().withMetadata(new ArtifactMetadata(map)).build());
    buildSourceParameters =
        artifactCollectionUtils.getBuildSourceParameters(jenkinsArtifactStream, settingAttribute, true, true);
    assertThat(buildSourceParameters).isNotNull();
    assertThat(buildSourceParameters.getBuildSourceRequestType())
        .isEqualTo(BuildSourceRequestType.GET_LAST_SUCCESSFUL_BUILD);
    assertThat(buildSourceParameters.getLimit()).isEqualTo(-1);
    assertThat(buildSourceParameters.getSavedBuildDetailsKeys()).contains("10");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetBuildSourceParametersForAmamzonS3() {
    // multi-artifact flag off
    AmazonS3ArtifactStream jenkinsArtifactStream = getS3ArtifactStream();
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(null);
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder().artifactType(ArtifactType.WAR).build());
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.limit(anyInt())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    Map<String, String> map = new HashMap<>();
    map.put("artifactPath", "myfolder/todolist.war");
    when(artifactIterator.next()).thenReturn(anArtifact().withMetadata(new ArtifactMetadata(map)).build());
    BuildSourceParameters buildSourceParameters =
        artifactCollectionUtils.getBuildSourceParameters(jenkinsArtifactStream, settingAttribute, true, true);
    assertThat(buildSourceParameters).isNotNull();
    assertThat(buildSourceParameters.getBuildSourceRequestType()).isEqualTo(BuildSourceRequestType.GET_BUILDS);
    assertThat(buildSourceParameters.getLimit()).isEqualTo(-1);
    assertThat(buildSourceParameters.getSavedBuildDetailsKeys()).contains("myfolder/todolist.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetNewBuildDetailsForAmazonS3() {
    Set<String> savedBuildDetailsKeys = new HashSet<>();
    savedBuildDetailsKeys.add("test folder2/todolist copy.war");
    savedBuildDetailsKeys.add("test folder2/test folder 3/todolist copy.war");
    savedBuildDetailsKeys.add("test folder2/");
    savedBuildDetailsKeys.add("test folder2/todolist.war");
    List<BuildDetails> buildDetails = new ArrayList<>();
    buildDetails.add(aBuildDetails().withArtifactPath("test folder2/test folder 3/todolist copy.war").build());
    buildDetails.add(aBuildDetails().withArtifactPath("test folder2/todolist copy.war").build());
    buildDetails.add(aBuildDetails().withArtifactPath("test folder2/todolist.war").build());
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .jobName("harness-example")
                                                            .artifactName("test folder2/")
                                                            .artifactType(ArtifactType.WAR)
                                                            .savedBuildDetailsKeys(savedBuildDetailsKeys)
                                                            .artifactStreamType(AMAZON_S3.name())
                                                            .build();
    List<BuildDetails> buildDetails1 = delegateArtifactCollectionUtils.getNewBuildDetails(
        savedBuildDetailsKeys, buildDetails, AMAZON_S3.name(), artifactStreamAttributes);
    assertThat(buildDetails1).isEmpty();

    buildDetails.add(aBuildDetails().withArtifactPath("new path").build());
    buildDetails1 = delegateArtifactCollectionUtils.getNewBuildDetails(
        savedBuildDetailsKeys, buildDetails, AMAZON_S3.name(), artifactStreamAttributes);
    assertThat(buildDetails1.size()).isEqualTo(1);
    assertThat(buildDetails1).extracting(BuildDetails::getArtifactPath).containsExactly("new path");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetNewBuildDetailsNoNewArtifacts() {
    Set<String> set = new HashSet<>();
    set.add("10");
    set.add("11");
    assertThat(delegateArtifactCollectionUtils.getNewBuildDetails(
                   set, asList(), AMAZON_S3.name(), ArtifactStreamAttributes.builder().build()))
        .isEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetNewBuildDetailsNoSavedArtifacts() {
    List<BuildDetails> buildDetails = delegateArtifactCollectionUtils.getNewBuildDetails(Collections.emptySet(),
        asList(aBuildDetails().withArtifactPath("todolist copy.war").build()), AMAZON_S3.name(),
        ArtifactStreamAttributes.builder().build());
    assertThat(buildDetails).isNotEmpty();
    assertThat(buildDetails).extracting(BuildDetails::getArtifactPath).containsExactly("todolist copy.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetDockerConfigWithoutCredentials() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(dockerArtifactStream);
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(DockerConfig.builder().dockerRegistryUrl("https://index.docker.io/v2/").build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    String dockerConfig = artifactCollectionUtils.getDockerConfig(ARTIFACT_STREAM_ID);
    assertThat(dockerConfig).isEqualTo("");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetDockerConfigWithCredentials() {
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(dockerArtifactStream);
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                                            .withValue(DockerConfig.builder()
                                                           .dockerRegistryUrl("https://index.docker.io/v2/")
                                                           .username(USER_NAME)
                                                           .password(PASSWORD)
                                                           .build())
                                            .withAccountId(ACCOUNT_ID)
                                            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    String dockerConfig = artifactCollectionUtils.getDockerConfig(ARTIFACT_STREAM_ID);
    assertThat(dockerConfig).isNotEqualTo("");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetDockerConfigWithGCRCredentials() {
    GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .appId(APP_ID)
                                              .sourceName("GCR_ARTIFACT_SOURCE")
                                              .serviceId(SERVICE_ID)
                                              .settingId(SETTING_ID)
                                              .registryHostName("registry")
                                              .dockerImageName("image")
                                              .build();
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(gcrArtifactStream);

    String serviceAccountFileContent = "{\n  \"key\": \"val\nue\"\n}";
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(GcpConfig.builder()
                           .accountId("GCP_ACCOUNT_ID")
                           .serviceAccountKeyFileContent(serviceAccountFileContent.toCharArray())
                           .build())
            .withAccountId(ACCOUNT_ID)
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);

    doNothing().when(managerDecryptionService).decrypt(any(EncryptableSetting.class), any());
    String dockerConfigEncoded = artifactCollectionUtils.getDockerConfig(ARTIFACT_STREAM_ID);
    assertThat(dockerConfigEncoded).isNotEqualTo("");
    String dockerConfig = decodeBase64ToString(dockerConfigEncoded);
    String username = JsonUtils.jsonPath(dockerConfig, "$.registry/image.username");
    assertThat(username).isEqualTo("_json_key");
    String password = JsonUtils.jsonPath(dockerConfig, "$.registry/image.password");
    assertThat(password.contains("\n")).isFalse();
    String value = JsonUtils.jsonPath(password, "$.key");
    assertThat(value).isEqualTo("value");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testRenderCustomArtifactScriptString() {
    CustomArtifactStream.Script script =
        CustomArtifactStream.Script.builder()
            .scriptString("echo hi")
            .customRepositoryMapping(CustomRepositoryMapping.builder()
                                         .artifactRoot("$.results")
                                         .buildNoPath("name")
                                         .artifactAttributes(asList(CustomRepositoryMapping.AttributeMapping.builder()
                                                                        .mappedAttribute("path")
                                                                        .relativePath("path")
                                                                        .build()))
                                         .build())
            .build();
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder().accountId(ACCOUNT_ID).scripts(asList(script)).build();
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream);
    assertThat(artifactStreamAttributes).isNotNull();
    assertThat(artifactStreamAttributes.getArtifactRoot()).isEqualTo("$.results");
    assertThat(artifactStreamAttributes.getBuildNoPath()).isEqualTo("name");
    assertThat(artifactStreamAttributes.getArtifactAttributes().size()).isEqualTo(1);
    assertThat(artifactStreamAttributes.getArtifactAttributes()).containsKey("path");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testRenderCustomArtifactScriptStringWithEmptyRoot() {
    CustomArtifactStream.Script script =
        CustomArtifactStream.Script.builder()
            .scriptString("echo hi")
            .customRepositoryMapping(CustomRepositoryMapping.builder()
                                         .buildNoPath("name")
                                         .artifactAttributes(asList(CustomRepositoryMapping.AttributeMapping.builder()
                                                                        .mappedAttribute("path")
                                                                        .relativePath("path")
                                                                        .build()))
                                         .build())
            .build();
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder().accountId(ACCOUNT_ID).scripts(asList(script)).build();
    artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testRenderCustomArtifactScriptStringWithEmptyBuildNo() {
    CustomArtifactStream.Script script =
        CustomArtifactStream.Script.builder()
            .scriptString("echo hi")
            .customRepositoryMapping(CustomRepositoryMapping.builder()
                                         .artifactRoot("$.results")
                                         .artifactAttributes(asList(CustomRepositoryMapping.AttributeMapping.builder()
                                                                        .mappedAttribute("path")
                                                                        .relativePath("path")
                                                                        .build()))
                                         .build())
            .build();
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder().accountId(ACCOUNT_ID).scripts(asList(script)).build();
    artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsCustom() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    CustomArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                                    .uuid(ARTIFACT_STREAM_ID)
                                                    .appId(APP_ID)
                                                    .sourceName("ARTIFACT_SOURCE")
                                                    .serviceId(SERVICE_ID)
                                                    .settingId(SETTING_ID)
                                                    .scripts(new ArrayList<>())
                                                    .build();
    customArtifactStream.setArtifactStreamType(CUSTOM.name());

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(customArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, customArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
    verify(buildSourceService, never()).getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCollectNewArtifactsCustomWithScript() {
    BuildDetails dockerBuildDetails = aBuildDetails().withNumber(LATEST_BUILD_NUMBER).build();
    CustomArtifactStream customArtifactStream =
        CustomArtifactStream.builder()
            .uuid(ARTIFACT_STREAM_ID)
            .appId(APP_ID)
            .sourceName("ARTIFACT_SOURCE")
            .serviceId(SERVICE_ID)
            .settingId(SETTING_ID)
            .scripts(Collections.singletonList(CustomArtifactStream.Script.builder().scriptString("Script").build()))
            .build();
    customArtifactStream.setArtifactStreamType(CUSTOM.name());
    customArtifactStream.setCollectionEnabled(false);

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);

    Artifact newArtifact = artifactCollectionUtils.getArtifact(customArtifactStream, dockerBuildDetails);
    when(artifactService.create(any(Artifact.class))).thenReturn(newArtifact);
    when(buildSourceService.getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID)).thenReturn(asList(dockerBuildDetails));

    Artifact collectedArtifact =
        artifactCollectionService.collectNewArtifacts(APP_ID, customArtifactStream, LATEST_BUILD_NUMBER);
    assertThat(collectedArtifact).isNotNull();
    assertThat(collectedArtifact.getBuildNo()).isEqualTo(LATEST_BUILD_NUMBER);
    verify(buildSourceService).getBuilds(APP_ID, ARTIFACT_STREAM_ID, SETTING_ID);
  }

  private AmazonS3ArtifactStream getS3ArtifactStream() {
    return AmazonS3ArtifactStream.builder()
        .uuid(ARTIFACT_STREAM_ID)
        .appId(APP_ID)
        .sourceName("ARTIFACT_SOURCE")
        .serviceId(SERVICE_ID)
        .settingId(SETTING_ID)
        .build();
  }

  private JenkinsArtifactStream getJenkinsArtifactStream() {
    return JenkinsArtifactStream.builder()
        .uuid(ARTIFACT_STREAM_ID)
        .appId(APP_ID)
        .sourceName("ARTIFACT_SOURCE")
        .serviceId(SERVICE_ID)
        .settingId(SETTING_ID)
        .jobname(JOB_NAME)
        .build();
  }

  private SmbArtifactStream getSmbArtifactStream() {
    return SmbArtifactStream.builder()
        .uuid(ARTIFACT_STREAM_ID)
        .appId(APP_ID)
        .sourceName("ARTIFACT_SOURCE")
        .serviceId(SERVICE_ID)
        .settingId(SETTING_ID)
        .build();
  }

  private ArtifactoryArtifactStream getArtifactoryArtifactStream() {
    return ArtifactoryArtifactStream.builder()
        .uuid(ARTIFACT_STREAM_ID)
        .appId(APP_ID)
        .sourceName("ARTIFACT_SOURCE")
        .serviceId(SERVICE_ID)
        .settingId(SETTING_ID)
        .build();
  }

  private BuildDetails getJenkinsBuildDetails() {
    return aBuildDetails()
        .withNumber("3594")
        .withRevision("12345")
        .withBuildUrl("https://jenkins.harness.io/job/portal/3594/")
        .build();
  }

  private BuildDetails getSmbBuildDetails() {
    return aBuildDetails()
        .withNumber("todolist")
        .withBuildUrl("smb:\\\\buildsrv.eastus.cloudapp.azure.com\\builds")
        .withArtifactPath("todolist")
        .withBuildDisplayName("displayName")
        .withBuildFullDisplayName("fullname")
        .build();
  }
}
