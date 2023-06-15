/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACTORY_URL;
import static software.wings.utils.WingsTestConstants.ARTIFACT_GROUP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_JOB_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.ArtifactMetadata;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.artifact.GcrArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.dto.SettingAttribute;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ArtifactService;

import com.google.inject.Inject;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.stream.Stream;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class BuildSourceCleanupHelperTest extends WingsBaseTest {
  private static final String ARTIFACT_STREAM_ID_1 = "ARTIFACT_STREAM_ID_1";
  private static final String ARTIFACT_STREAM_ID_2 = "ARTIFACT_STREAM_ID_2";
  private static final String ARTIFACT_STREAM_ID_3 = "ARTIFACT_STREAM_ID_3";
  private static final String ARTIFACT_STREAM_ID_4 = "ARTIFACT_STREAM_ID_4";
  private static final String ARTIFACT_STREAM_ID_5 = "ARTIFACT_STREAM_ID_5";
  private static final String ARTIFACT_STREAM_ID_6 = "ARTIFACT_STREAM_ID_6";
  private static final String ARTIFACT_STREAM_ID_7 = "ARTIFACT_STREAM_ID_7";
  private static final String ARTIFACT_STREAM_ID_8 = "ARTIFACT_STREAM_ID_8";
  private static final String ARTIFACT_STREAM_ID_9 = "ARTIFACT_STREAM_ID_9";

  @Mock private ArtifactCollectionUtils artifactCollectionUtils;
  @Mock private ArtifactService artifactService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private Query query;
  @Mock private MorphiaIterator<Artifact, Artifact> artifactIterator;

  @InjectMocks @Inject private BuildSourceCleanupHelper buildSourceCleanupHelper;

  private final ArtifactStream ARTIFACT_STREAM_UNSTABLE = DockerArtifactStream.builder()
                                                              .uuid(ARTIFACT_STREAM_ID_2)
                                                              .sourceName(ARTIFACT_STREAM_NAME)
                                                              .appId(APP_ID)
                                                              .settingId(SETTING_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .imageName("image_name")
                                                              .build();

  private final ArtifactStream dockerArtifactStream = DockerArtifactStream.builder()
                                                          .uuid(ARTIFACT_STREAM_ID_1)
                                                          .sourceName(ARTIFACT_STREAM_NAME)
                                                          .appId(APP_ID)
                                                          .settingId(SETTING_ID)
                                                          .serviceId(SERVICE_ID)
                                                          .imageName("image_name")
                                                          .build();

  private final AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .uuid(ARTIFACT_STREAM_ID_3)
                                                          .appId(APP_ID)
                                                          .settingId(SETTING_ID)
                                                          .region("us-east-1")
                                                          .autoPopulate(true)
                                                          .serviceId(SERVICE_ID)
                                                          .build();

  private final ArtifactoryArtifactStream artifactoryStream = ArtifactoryArtifactStream.builder()
                                                                  .uuid(ARTIFACT_STREAM_ID)
                                                                  .appId(APP_ID)
                                                                  .sourceName("ARTIFACT_SOURCE")
                                                                  .serviceId(SERVICE_ID)
                                                                  .build();

  private final GcrArtifactStream gcrArtifactStream = GcrArtifactStream.builder()
                                                          .uuid(ARTIFACT_STREAM_ID_5)
                                                          .appId(APP_ID)
                                                          .sourceName("ARTIFACT_SOURCE")
                                                          .serviceId(SERVICE_ID)
                                                          .build();

  private final EcrArtifactStream ecrArtifactStream = EcrArtifactStream.builder()
                                                          .uuid(ARTIFACT_STREAM_ID_6)
                                                          .appId(APP_ID)
                                                          .sourceName("ARTIFACT_SOURCE")
                                                          .serviceId(SERVICE_ID)
                                                          .settingId(SETTING_ID)
                                                          .build();

  private final JenkinsArtifactStream jenkinsArtifactStream = JenkinsArtifactStream.builder()
                                                                  .uuid(ARTIFACT_STREAM_ID_9)
                                                                  .sourceName("todolistwar")
                                                                  .settingId(SETTING_ID)
                                                                  .accountId(ACCOUNT_ID)
                                                                  .appId(APP_ID)
                                                                  .jobname("todolistwar")
                                                                  .autoPopulate(true)
                                                                  .serviceId(SERVICE_ID)
                                                                  .artifactPaths(asList("target/todolist.war"))
                                                                  .build();

  private final NexusArtifactStream nexusArtifactStream = NexusArtifactStream.builder()
                                                              .uuid(ARTIFACT_STREAM_ID_7)
                                                              .appId(APP_ID)
                                                              .settingId(SETTING_ID)
                                                              .sourceName(ARTIFACT_STREAM_NAME)
                                                              .jobname(BUILD_JOB_NAME)
                                                              .groupId(ARTIFACT_GROUP_ID)
                                                              .artifactPaths(Stream.of(ARTIFACT_NAME).collect(toList()))
                                                              .build();

  private final AcrArtifactStream acrArtifactStream = AcrArtifactStream.builder()
                                                          .uuid(ARTIFACT_STREAM_ID_8)
                                                          .appId(APP_ID)
                                                          .sourceName("ARTIFACT_SOURCE")
                                                          .serviceId(SERVICE_ID)
                                                          .settingId(SETTING_ID)
                                                          .build();

  private SettingAttribute artifactorySetting = SettingAttribute.builder()
                                                    .uuid(SETTING_ID)
                                                    .value(ArtifactoryConfig.builder()
                                                               .artifactoryUrl(ARTIFACTORY_URL)
                                                               .username("admin")
                                                               .password("dummy123!".toCharArray())
                                                               .build())
                                                    .build();

  ArtifactStreamAttributes artifactStreamAttributesForArtifactory =
      ArtifactStreamAttributes.builder()
          .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
          .metadataOnly(true)
          .serverSetting(artifactorySetting)
          .artifactStreamId(ARTIFACT_STREAM_ID_4)
          .artifactServerEncryptedDataDetails(Collections.emptyList())
          .build();

  Artifact artifact = Artifact.Builder.anArtifact()
                          .withUuid(ARTIFACT_ID)
                          .withArtifactStreamId(ARTIFACT_STREAM_ID)
                          .withAppId(APP_ID)
                          .withSettingId(SETTING_ID)
                          .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                          .withRevision("1.0")
                          .build();

  private static final Artifact ARTIFACT_1 =
      anArtifact().withMetadata(new ArtifactMetadata(Maps.newHashMap("buildNo", "1"))).build();
  private static final Artifact ARTIFACT_2 =
      anArtifact().withMetadata(new ArtifactMetadata(Maps.newHashMap("buildNo", "2"))).build();

  private static final BuildDetails BUILD_DETAILS_1 = aBuildDetails().withNumber("1").withArtifactPath("a").build();
  private static final BuildDetails BUILD_DETAILS_2 = aBuildDetails().withNumber("2").withArtifactPath("b").build();

  @Before
  public void setupMocks() {
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(artifactCollectionUtils.getArtifact(ARTIFACT_STREAM_UNSTABLE, BUILD_DETAILS_1)).thenReturn(ARTIFACT_1);
    when(artifactCollectionUtils.getArtifact(ARTIFACT_STREAM_UNSTABLE, BUILD_DETAILS_2)).thenReturn(ARTIFACT_2);
    when(artifactCollectionUtils.getArtifact(dockerArtifactStream, BUILD_DETAILS_1)).thenReturn(ARTIFACT_1);
    when(artifactCollectionUtils.getArtifact(dockerArtifactStream, BUILD_DETAILS_2)).thenReturn(ARTIFACT_2);

    when(artifactCollectionUtils.getArtifactStreamAttributes(artifactoryStream, false))
        .thenReturn(artifactStreamAttributesForArtifactory);

    when(artifactService.create(ARTIFACT_1)).thenReturn(ARTIFACT_1);
    when(artifactService.create(ARTIFACT_2)).thenReturn(ARTIFACT_2);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withArtifactoryDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.limit(NO_LIMIT)).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, artifactoryStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withGCRDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.limit(NO_LIMIT)).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, gcrArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withECRDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.limit(NO_LIMIT)).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, ecrArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withNexusDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.limit(NO_LIMIT)).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(
        ACCOUNT_ID, nexusArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withDockerDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(
        ACCOUNT_ID, dockerArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithEmptyBuilds() {
    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, dockerArtifactStream, Collections.EMPTY_LIST);
    verify(artifactService, never()).prepareCleanupQuery(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withAMIDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, amiArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void withACRDeleteArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.limit(NO_LIMIT)).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, acrArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldNotDeleteJENKINSArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupHelper.cleanupArtifacts(
        ACCOUNT_ID, jenkinsArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(0)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void shouldSkipDeleteWithEmptyArtifacts() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(null);

    buildSourceCleanupHelper.cleanupArtifacts(ACCOUNT_ID, amiArtifactStream, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactService, times(0)).deleteArtifacts(any());
  }
}
