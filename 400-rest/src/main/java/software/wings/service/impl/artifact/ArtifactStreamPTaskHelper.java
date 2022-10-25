/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactCollectionPTaskClientParams;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.validation.Validator;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ArtifactStreamPTaskHelper {
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private PerpetualTaskService perpetualTaskService;

  private static final String ARTIFACT_STREAM_ID = "artifactStreamId";

  public void createPerpetualTask(ArtifactStream artifactStream) {
    Validator.notNullCheck("Artifact stream id is missing", artifactStream.getUuid());
    if (artifactStream.getPerpetualTaskId() != null) {
      throw new InvalidRequestException(
          format("Perpetual task already exists for artifact stream: %s", artifactStream.getUuid()));
    }

    try (AutoLogContext ignore1 = new AccountLogContext(artifactStream.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ArtifactStreamLogContext(artifactStream.getUuid(), OVERRIDE_ERROR)) {
      ArtifactCollectionPTaskClientParams artifactCollectionPTaskClientParams =
          ArtifactCollectionPTaskClientParams.builder().artifactStreamId(artifactStream.getUuid()).build();
      log.info("Creating perpetual task");

      String perpetualTaskId = create(artifactStream.getAccountId(), artifactCollectionPTaskClientParams);
      log.info("Created perpetual task: {}", perpetualTaskId);

      boolean updated = false;
      try {
        updated = artifactStreamService.attachPerpetualTaskId(artifactStream, perpetualTaskId);
        log.info("Attaching perpetual task: {} to artifact stream: {}", perpetualTaskId, artifactStream.getUuid());
      } finally {
        if (!updated) {
          // If artifact stream is not updated, it doesn't know about the perpetual task. So the perpetual task becomes
          // a zombie and is never reset or deleted on config change. It might try to do regular collection along with
          // perpetual task which can lead to race conditions.
          deletePerpetualTask(artifactStream.getAccountId(), perpetualTaskId);
        }
      }
    } catch (Exception ex) {
      // This is background-type operation. Artifact stream can be created but perpetual task creation can fail. We
      // should not fail the save operation and try assigning to perpetual task later.
      log.error(format("Unable to create perpetual task for artifact stream: %s", artifactStream.getUuid()), ex);
    }
  }

  private String create(String accountId, ArtifactCollectionPTaskClientParams clientParams) {
    Map<String, String> clientParamMap = new HashMap<>();
    if (isEmpty(clientParams.getArtifactStreamId())) {
      throw new InvalidRequestException(
          "Failed to create Perpetual Task as Artifact Stream Id is missing from clientParams");
    }
    clientParamMap.put(ARTIFACT_STREAM_ID, clientParams.getArtifactStreamId());

    PerpetualTaskClientContext clientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(1))
                                         .setTimeout(Durations.fromMinutes(2))
                                         .build();
    return perpetualTaskService.createTask(
        PerpetualTaskType.ARTIFACT_COLLECTION, accountId, clientContext, schedule, false, "");
  }

  public void deletePerpetualTask(String accountId, String perpetualTaskId) {
    artifactStreamService.detachPerpetualTaskId(perpetualTaskId);
    if (!perpetualTaskService.deleteTask(accountId, perpetualTaskId)) {
      log.error(format("Unable to delete artifact collection perpetual task: %s", perpetualTaskId));
    }
  }
}
