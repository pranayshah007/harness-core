/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifact;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.artifact.ArtifactStreamCollectionStatus.UNSTABLE;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.rule.Owner;

import software.wings.beans.alert.AlertType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamCollectionStatus;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.delegatetasks.buildsource.BuildSourceExecutionResponse;
import software.wings.delegatetasks.buildsource.BuildSourceResponse;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.artifact.ArtifactStreamPTaskHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;
import java.util.HashSet;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactCollectionResponseHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";

  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;
  @Mock private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;
  @Mock private ArtifactService artifactService;
  @Mock private TriggerService triggerService;
  @Mock private PerpetualTaskService perpetualTaskService;
  @Mock private AlertService alertService;
  @Mock private FeatureFlagService featureFlagService;

  @Inject @InjectMocks private ArtifactCollectionResponseHandler artifactCollectionResponseHandler;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final BuildDetails BUILD_DETAILS_1 = aBuildDetails().withNumber("1").build();
  private static final BuildDetails BUILD_DETAILS_2 = aBuildDetails().withNumber("2").build();
  private final ArtifactStream ARTIFACT_STREAM = DockerArtifactStream.builder()
                                                     .uuid(ARTIFACT_STREAM_ID)
                                                     .sourceName(ARTIFACT_STREAM_NAME)
                                                     .appId(APP_ID)
                                                     .settingId(SETTING_ID)
                                                     .serviceId(SERVICE_ID)
                                                     .imageName("image_name")
                                                     .accountId(ACCOUNT_ID)
                                                     .build();

  private final String ARTIFACT_STREAM_ID_2 = "ARTIFACT_STREAM_ID_2";
  private final ArtifactStream ARTIFACT_STREAM_UNSTABLE = DockerArtifactStream.builder()
                                                              .uuid(ARTIFACT_STREAM_ID_2)
                                                              .sourceName(ARTIFACT_STREAM_NAME)
                                                              .appId(APP_ID)
                                                              .settingId(SETTING_ID)
                                                              .serviceId(SERVICE_ID)
                                                              .imageName("image_name")
                                                              .accountId(ACCOUNT_ID)
                                                              .build();

  private static final Artifact ARTIFACT_1 =
      anArtifact().withMetadata(new ArtifactMetadata(Maps.newHashMap("buildNo", "1"))).build();
  private static final Artifact ARTIFACT_2 =
      anArtifact().withMetadata(new ArtifactMetadata(Maps.newHashMap("buildNo", "2"))).build();

  @Before
  public void setup() {
    ARTIFACT_STREAM.setPerpetualTaskId(PERPETUAL_TASK_ID);
    ARTIFACT_STREAM_UNSTABLE.setPerpetualTaskId(PERPETUAL_TASK_ID);
    ARTIFACT_STREAM_UNSTABLE.setCollectionStatus(UNSTABLE.name());
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(ARTIFACT_STREAM);
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_2)).thenReturn(ARTIFACT_STREAM_UNSTABLE);
    when(featureFlagService.isEnabled(eq(FeatureName.ARTIFACT_PERPETUAL_TASK), any())).thenReturn(true);
    when(featureFlagService.isEnabled(eq(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleInvalidArtifactStream() {
    BuildSourceExecutionResponse buildSourceExecutionResponse = constructBuildSourceExecutionResponse(false, true);
    buildSourceExecutionResponse.setArtifactStreamId("random");
    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);
    verify(artifactStreamPTaskHelper).deletePerpetualTask(ACCOUNT_ID, PERPETUAL_TASK_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotProcessWhenFeatureDisabled() {
    when(featureFlagService.isEnabled(eq(FeatureName.ARTIFACT_PERPETUAL_TASK), any())).thenReturn(false);
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .build();
    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);
    verify(perpetualTaskService, never()).resetTask(ACCOUNT_ID, PERPETUAL_TASK_ID, null);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotProcessFailedResponse() {
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .build();
    ARTIFACT_STREAM.setFailedCronAttempts(3499);
    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);
    verify(perpetualTaskService, times(1)).resetTask(ACCOUNT_ID, PERPETUAL_TASK_ID, null);
    verify(alertService, times(1)).openAlert(eq(ACCOUNT_ID), any(), eq(AlertType.ARTIFACT_COLLECTION_FAILED), any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldProcessSuccessfulResponse() {
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .buildSourceResponse(BuildSourceResponse.builder().cleanup(false).build())
            .build();
    ARTIFACT_STREAM.setFailedCronAttempts(10);
    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);
    verify(artifactStreamService, times(1))
        .updateFailedCronAttemptsAndLastIteration(ACCOUNT_ID, ARTIFACT_STREAM_ID, 0, true);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteTrigger() {
    BuildSourceExecutionResponse buildSourceExecutionResponse = constructBuildSourceExecutionResponse(false, true);

    when(artifactCollectionUtils.processBuilds(ARTIFACT_STREAM_UNSTABLE, asList(BUILD_DETAILS_1, BUILD_DETAILS_2)))
        .thenReturn(asList(ARTIFACT_1, ARTIFACT_2));

    artifactCollectionResponseHandler.handleResponseInternal(ARTIFACT_STREAM_UNSTABLE, buildSourceExecutionResponse);

    verify(artifactCollectionUtils).processBuilds(ARTIFACT_STREAM_UNSTABLE, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(triggerService)
        .triggerExecutionPostArtifactCollectionAsync(
            ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID_2, asList(ARTIFACT_1, ARTIFACT_2));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotExecuteTrigger() {
    BuildSourceExecutionResponse buildSourceExecutionResponse = constructBuildSourceExecutionResponse(false, false);

    when(artifactCollectionUtils.processBuilds(ARTIFACT_STREAM_UNSTABLE, asList(BUILD_DETAILS_1, BUILD_DETAILS_2)))
        .thenReturn(asList(ARTIFACT_1, ARTIFACT_2));

    artifactCollectionResponseHandler.handleResponseInternal(ARTIFACT_STREAM_UNSTABLE, buildSourceExecutionResponse);

    verify(artifactCollectionUtils).processBuilds(ARTIFACT_STREAM_UNSTABLE, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(triggerService, never())
        .triggerExecutionPostArtifactCollectionAsync(
            ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID_2, asList(ARTIFACT_1, ARTIFACT_2));
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldHandleCleanup() {
    BuildSourceExecutionResponse buildSourceExecutionResponse = constructBuildSourceExecutionResponse(true, false);
    when(artifactService.deleteArtifactsByUniqueKey(any(), any(), any())).thenReturn(true);
    artifactCollectionResponseHandler.handleResponseInternal(ARTIFACT_STREAM_UNSTABLE, buildSourceExecutionResponse);
    verify(artifactService).deleteArtifactsByUniqueKey(eq(ARTIFACT_STREAM_UNSTABLE), any(), any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateStatusWhenSuccess() {
    BuildSourceExecutionResponse buildSourceExecutionResponse = constructBuildSourceExecutionResponse(false, true);

    when(artifactCollectionUtils.processBuilds(ARTIFACT_STREAM_UNSTABLE, asList(BUILD_DETAILS_1, BUILD_DETAILS_2)))
        .thenReturn(asList(ARTIFACT_1, ARTIFACT_2));
    when(artifactStreamService.get(ARTIFACT_STREAM_ID_2)).thenReturn(ARTIFACT_STREAM_UNSTABLE);

    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);

    verify(artifactCollectionUtils).processBuilds(ARTIFACT_STREAM_UNSTABLE, asList(BUILD_DETAILS_1, BUILD_DETAILS_2));
    verify(artifactStreamService).updateLastIterationFields(ACCOUNT_ID, ARTIFACT_STREAM_UNSTABLE.getUuid(), true);
    verify(artifactStreamService)
        .updateCollectionStatus(
            ACCOUNT_ID, ARTIFACT_STREAM_UNSTABLE.getUuid(), ArtifactStreamCollectionStatus.STABLE.name());
    verify(triggerService)
        .triggerExecutionPostArtifactCollectionAsync(
            ACCOUNT_ID, APP_ID, ARTIFACT_STREAM_ID_2, asList(ARTIFACT_1, ARTIFACT_2));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateStatusWhenFailureMaxAttempts() {
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .build();
    ARTIFACT_STREAM.setFailedCronAttempts(3499);
    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);
    verify(perpetualTaskService, times(1)).resetTask(ACCOUNT_ID, PERPETUAL_TASK_ID, null);
    verify(alertService, times(1)).openAlert(eq(ACCOUNT_ID), any(), eq(AlertType.ARTIFACT_COLLECTION_FAILED), any());
    verify(artifactStreamService, times(1))
        .updateCollectionStatus(ACCOUNT_ID, ARTIFACT_STREAM_ID, ArtifactStreamCollectionStatus.STOPPED.name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldUpdateStatusWhenFailure() {
    BuildSourceExecutionResponse buildSourceExecutionResponse =
        BuildSourceExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .artifactStreamId(ARTIFACT_STREAM_ID)
            .build();

    artifactCollectionResponseHandler.processArtifactCollectionResult(
        ACCOUNT_ID, PERPETUAL_TASK_ID, buildSourceExecutionResponse);
    verify(artifactStreamService, times(1)).updateCollectionStatus(ACCOUNT_ID, ARTIFACT_STREAM_ID, UNSTABLE.name());
    verify(artifactStreamService).updateFailedCronAttemptsAndLastIteration(ACCOUNT_ID, ARTIFACT_STREAM_ID, 1, false);
  }

  private BuildSourceExecutionResponse constructBuildSourceExecutionResponse(boolean cleanup, boolean stable) {
    BuildSourceResponse buildSourceResponse;
    if (cleanup) {
      buildSourceResponse =
          BuildSourceResponse.builder()
              .toBeDeletedKeys(new HashSet<>(asList(BUILD_DETAILS_1.getNumber(), BUILD_DETAILS_2.getNumber())))
              .cleanup(true)
              .build();
    } else {
      buildSourceResponse =
          BuildSourceResponse.builder().buildDetails(asList(BUILD_DETAILS_1, BUILD_DETAILS_2)).stable(stable).build();
    }

    return BuildSourceExecutionResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactStreamId(ARTIFACT_STREAM_ID_2)
        .buildSourceResponse(buildSourceResponse)
        .build();
  }
}
