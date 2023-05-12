/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.impl;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.perpetualtask.PerpetualTaskType.ARTIFACT_COLLECTION_NG;
import static io.harness.perpetualtask.PerpetualTaskType.GITPOLLING_NG;
import static io.harness.perpetualtask.PerpetualTaskType.MANIFEST_COLLECTION_NG;

import io.harness.NgAutoLogContext;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitpolling.bean.GitPollingConfig;
import io.harness.delegate.AccountId;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgPollingAutoLogContext;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.polling.bean.GitPollingInfo;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingType;
import io.harness.polling.service.impl.artifact.ArtifactPerpetualTaskHelperNg;
import io.harness.polling.service.impl.gitpolling.GitPollingPerpetualTaskHelperNg;
import io.harness.polling.service.impl.manifest.ManifestPerpetualTaskHelperNg;
import io.harness.polling.service.intfc.PollingPerpetualTaskService;
import io.harness.polling.service.intfc.PollingService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.util.Durations;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PollingPerpetualTaskServiceImpl implements PollingPerpetualTaskService {
  ManifestPerpetualTaskHelperNg manifestPerpetualTaskHelperNg;
  ArtifactPerpetualTaskHelperNg artifactPerpetualTaskHelperNg;
  GitPollingPerpetualTaskHelperNg gitPollingPerpetualTaskHelperNg;
  DelegateServiceGrpcClient delegateServiceGrpcClient;
  PollingService pollingService;

  @Override
  public void createPerpetualTask(PollingDocument pollingDocument) {
    try (AutoLogContext ignore1 = new NgAutoLogContext(pollingDocument.getProjectIdentifier(),
             pollingDocument.getOrgIdentifier(), pollingDocument.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new NgPollingAutoLogContext(pollingDocument.getUuid(), OVERRIDE_ERROR);) {
      String pollingDocId = pollingDocument.getUuid();
      PollingType pollingType = pollingDocument.getPollingType();
      PerpetualTaskExecutionBundle executionBundle;
      String perpetualTaskType;
      PerpetualTaskSchedule schedule;
      switch (pollingType) {
        case MANIFEST:
          executionBundle = manifestPerpetualTaskHelperNg.createPerpetualTaskExecutionBundle(pollingDocument);
          perpetualTaskType = MANIFEST_COLLECTION_NG;
          schedule = PerpetualTaskSchedule.newBuilder()
                         .setInterval(Durations.fromMinutes(2))
                         .setTimeout(Durations.fromMinutes(3))
                         .build();
          break;
        case ARTIFACT:
          executionBundle = artifactPerpetualTaskHelperNg.createPerpetualTaskExecutionBundle(pollingDocument);
          perpetualTaskType = ARTIFACT_COLLECTION_NG;
          schedule = PerpetualTaskSchedule.newBuilder()
                         .setInterval(Durations.fromMinutes(1))
                         .setTimeout(Durations.fromMinutes(2))
                         .build();
          break;
        case WEBHOOK_POLLING:
          executionBundle = gitPollingPerpetualTaskHelperNg.createPerpetualTaskExecutionBundle(pollingDocument);
          GitPollingInfo gitPollingInfo = (GitPollingInfo) pollingDocument.getPollingInfo();
          GitPollingConfig pollingConfig = gitPollingInfo.toGitPollingConfig();
          int pollInterval = pollingConfig.getPollInterval();

          perpetualTaskType = GITPOLLING_NG;
          schedule = PerpetualTaskSchedule.newBuilder()
                         .setInterval(Durations.fromMinutes(pollInterval))
                         .setTimeout(Durations.fromMinutes(pollInterval + 1))
                         .build();
          break;
        default:
          throw new InvalidRequestException(String.format("Unsupported category %s for polling", pollingType));
      }

      PerpetualTaskClientContextDetails taskContext =
          PerpetualTaskClientContextDetails.newBuilder().setExecutionBundle(executionBundle).build();

      AccountId accountId = AccountId.newBuilder().setId(pollingDocument.getAccountId()).build();

      PerpetualTaskId perpetualTaskId = delegateServiceGrpcClient.createPerpetualTask(accountId, perpetualTaskType,
          schedule, taskContext, false, pollingType.name() + " Collection Task", pollingDocId);

      log.info("Perpetual task created with id {}  for perpetualTaskType {} and pollingDocumentId {}",
          perpetualTaskId.getId(), perpetualTaskType, pollingDocId);

      if (!pollingService.attachPerpetualTask(pollingDocument.getAccountId(), pollingDocId, perpetualTaskId.getId())) {
        log.error("Unable to attach perpetual task {} to pollingDocId {}", perpetualTaskId, pollingDocId);
        deletePerpetualTask(perpetualTaskId.getId(), pollingDocument.getAccountId());
      }
    }
  }

  @Override
  public void resetPerpetualTask(PollingDocument pollingDocument) {
    delegateServiceGrpcClient.resetPerpetualTask(AccountId.newBuilder().setId(pollingDocument.getAccountId()).build(),
        PerpetualTaskId.newBuilder().setId(pollingDocument.getPerpetualTaskId()).build(),
        getExecutionBundle(pollingDocument));
  }

  @Override
  public void deletePerpetualTask(String perpetualTaskId, String accountId) {
    delegateServiceGrpcClient.deletePerpetualTask(
        AccountId.newBuilder().setId(accountId).build(), PerpetualTaskId.newBuilder().setId(perpetualTaskId).build());
  }

  private PerpetualTaskExecutionBundle getExecutionBundle(PollingDocument pollingDocument) {
    if (PollingType.MANIFEST.equals(pollingDocument.getPollingType())) {
      return manifestPerpetualTaskHelperNg.createPerpetualTaskExecutionBundle(pollingDocument);
    } else if (PollingType.ARTIFACT.equals(pollingDocument.getPollingType())) {
      return artifactPerpetualTaskHelperNg.createPerpetualTaskExecutionBundle(pollingDocument);
    } else {
      throw new InvalidRequestException(
          String.format("Unsupported category %s for polling", pollingDocument.getPollingType()));
    }
  }
}
