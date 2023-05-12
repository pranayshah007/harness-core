/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.buildsource;

import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.HARSH;

import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.UNSTABLE;
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
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactMetadataKeys;
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
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Stream;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BuildSourceCleanupCallbackTest extends WingsBaseTest {
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
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private Query query;
  @Mock private MorphiaIterator<Artifact, Artifact> artifactIterator;
  @Mock private ExecutorService executorService;
  private BuildSourceCleanupHelper buildSourceCleanupHelper;

  @InjectMocks @Inject private BuildSourceCleanupCallback buildSourceCleanupCallback;

  private final ArtifactStream ARTIFACT_STREAM_UNSTABLE = DockerArtifactStream.builder()
                                                              .uuid(ARTIFACT_STREAM_ID_2)
                                                              .sourceName(ARTIFACT_STREAM_NAME)
                                                              .appId(APP_ID)
                                                              .settingId(SETTING_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .imageName("image_name")
                                                              .build();

  private final ArtifactStream ARTIFACT_STREAM = DockerArtifactStream.builder()
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
    ARTIFACT_STREAM_UNSTABLE.setCollectionStatus(UNSTABLE.name());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(ARTIFACT_STREAM);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_1)).thenReturn(ARTIFACT_STREAM);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_3)).thenReturn(amiArtifactStream);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_4)).thenReturn(artifactoryStream);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_5)).thenReturn(gcrArtifactStream);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_6)).thenReturn(ecrArtifactStream);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_7)).thenReturn(nexusArtifactStream);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_8)).thenReturn(acrArtifactStream);
    when(artifactCollectionUtils.getArtifact(ARTIFACT_STREAM_UNSTABLE, BUILD_DETAILS_1)).thenReturn(ARTIFACT_1);
    when(artifactCollectionUtils.getArtifact(ARTIFACT_STREAM_UNSTABLE, BUILD_DETAILS_2)).thenReturn(ARTIFACT_2);
    when(artifactCollectionUtils.getArtifact(ARTIFACT_STREAM, BUILD_DETAILS_1)).thenReturn(ARTIFACT_1);
    when(artifactCollectionUtils.getArtifact(ARTIFACT_STREAM, BUILD_DETAILS_2)).thenReturn(ARTIFACT_2);

    when(artifactCollectionUtils.getArtifactStreamAttributes(artifactoryStream, false))
        .thenReturn(artifactStreamAttributesForArtifactory);

    when(artifactService.create(ARTIFACT_1)).thenReturn(ARTIFACT_1);
    when(artifactService.create(ARTIFACT_2)).thenReturn(ARTIFACT_2);
    buildSourceCleanupCallback.setAccountId(ACCOUNT_ID);
    buildSourceCleanupHelper =
        new BuildSourceCleanupHelper(artifactService, featureFlagService, artifactCollectionUtils);
    buildSourceCleanupCallback.setBuildSourceCleanupHelper(buildSourceCleanupHelper);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithArtifactoryDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_4);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(query.limit(NO_LIMIT)).thenReturn(query);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), artifactoryStream);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithGCRDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_5);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(query.limit(NO_LIMIT)).thenReturn(query);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), gcrArtifactStream);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithECRDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_6);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(query.limit(NO_LIMIT)).thenReturn(query);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), ecrArtifactStream);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithNexusDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_7);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(query.limit(NO_LIMIT)).thenReturn(query);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), nexusArtifactStream);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithEmptyBuilds() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_1);

    BuildSourceExecutionResponse buildSourceExecutionResponse = prepareBuildSourceExecutionResponse(true);
    buildSourceExecutionResponse.getBuildSourceResponse().setBuildDetails(emptyList());
    buildSourceCleanupCallback.handleResponseForSuccessInternal(buildSourceExecutionResponse, ARTIFACT_STREAM);
    verify(artifactService, never()).prepareArtifactWithMetadataQuery(any(), anyBoolean());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_1);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(anArtifact().build());

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), ARTIFACT_STREAM);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithAMIDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_3);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), amiArtifactStream);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithACRDeleteArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_8);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);
    when(query.limit(NO_LIMIT)).thenReturn(query);
    when(artifactService.prepareCleanupQuery(any())).thenReturn(query);
    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), acrArtifactStream);
    verify(artifactService, times(1)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldNotDeleteJENKINSArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_9);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(artifact);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), jenkinsArtifactStream);
    verify(artifactService, times(0)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldSkipDeleteWithEmptyArtifacts() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_3);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(null);

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), amiArtifactStream);
    verify(artifactService, times(0)).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccess() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_1);
    when(artifactService.prepareArtifactWithMetadataQuery(any(), anyBoolean())).thenReturn(query);
    when(query.fetch()).thenReturn(artifactIterator);

    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    Map<String, String> metadataMap = new HashMap<>();
    metadataMap.put(ArtifactMetadataKeys.buildNo, "1");
    when(artifactIterator.next()).thenReturn(anArtifact().withMetadata(new ArtifactMetadata(metadataMap)).build());

    buildSourceCleanupCallback.handleResponseForSuccessInternal(
        prepareBuildSourceExecutionResponse(true), ARTIFACT_STREAM);
    verify(artifactService, never()).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldNotifyOnSuccessWithNullResponse() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_1);
    BuildSourceExecutionResponse buildSourceExecutionResponse = prepareBuildSourceExecutionResponse(true);
    buildSourceExecutionResponse.setBuildSourceResponse(null);
    buildSourceCleanupCallback.handleResponseForSuccessInternal(buildSourceExecutionResponse, ARTIFACT_STREAM);
    verify(artifactService, never()).deleteArtifacts(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNotifyWithExecutorRejectedQueueException() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_1);
    when(executorService.submit(any(Runnable.class))).thenThrow(RejectedExecutionException.class);
    BuildSourceExecutionResponse buildSourceExecutionResponse = prepareBuildSourceExecutionResponse(true);
    buildSourceExecutionResponse.getBuildSourceResponse().setBuildDetails(null);

    buildSourceCleanupCallback.notify(Maps.newHashMap("", prepareBuildSourceExecutionResponse(true)));
    verify(executorService, times(1)).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNotifyOnErrorNotifyResponseDataResponse() {
    buildSourceCleanupCallback.setArtifactStreamId(ARTIFACT_STREAM_ID_1);
    buildSourceCleanupCallback.notify(Maps.newHashMap("", ErrorNotifyResponseData.builder().build()));
    verify(executorService, never()).submit(any(Runnable.class));
  }

  private BuildSourceExecutionResponse prepareBuildSourceExecutionResponse(boolean stable) {
    return BuildSourceExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .buildSourceResponse(
            BuildSourceResponse.builder().buildDetails(asList(BUILD_DETAILS_1, BUILD_DETAILS_2)).stable(stable).build())
        .build();
  }
}
