/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.applicationmanifest;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.manifests.request.ManifestCollectionPTaskClientParams.ManifestCollectionPTaskClientParamsKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.validation.Validator;

import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.delegatetasks.manifest.ApplicationManifestLogContext;
import software.wings.service.intfc.ApplicationManifestService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Duration;
import com.google.protobuf.util.Durations;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Singleton
@TargetModule(_870_CG_ORCHESTRATION)
public class AppManifestPTaskHelper {
  public static final Duration ITERATION_INTERVAL = Durations.fromSeconds(90);
  public static final Duration TIMEOUT = Durations.fromMinutes(3);
  @Inject ApplicationManifestService applicationManifestService;
  @Inject PerpetualTaskService perpetualTaskService;

  public void createPerpetualTask(ApplicationManifest appManifest) {
    final String appManifestId = appManifest.getUuid();
    Validator.notNullCheck("Application manifest id is missing", appManifestId);
    if (appManifest.getPerpetualTaskId() != null) {
      throw new InvalidRequestException("A perpetual task is already assigned to this application manifest with id: "
          + appManifest.getPerpetualTaskId());
    }

    final String accountId = appManifest.getAccountId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ApplicationManifestLogContext(appManifestId, OVERRIDE_ERROR)) {
      log.info("Creating perpetual task ");

      PerpetualTaskClientContext clientContext =
          PerpetualTaskClientContext.builder()
              .clientParams(ImmutableMap.of(ManifestCollectionPTaskClientParamsKeys.appManifestId, appManifestId,
                  ManifestCollectionPTaskClientParamsKeys.appId, appManifest.getAppId()))
              .build();
      PerpetualTaskSchedule schedule =
          PerpetualTaskSchedule.newBuilder().setInterval(ITERATION_INTERVAL).setTimeout(TIMEOUT).build();
      String perpetualTaskId = perpetualTaskService.createTask(PerpetualTaskType.MANIFEST_COLLECTION, accountId,
          clientContext, schedule, false, "Task to collect manifests for app manifest " + appManifestId);

      Validator.notNullCheck("Error in creating perpetual task for app manifest " + appManifestId, perpetualTaskId);
      if (!applicationManifestService.attachPerpetualTask(accountId, appManifestId, perpetualTaskId)) {
        log.error("Unable to attach perpetual task {}", perpetualTaskId);
        deletePerpetualTask(perpetualTaskId, appManifestId, accountId);
      }

      log.info("Perpetual task created successfully");
    } catch (Exception e) {
      log.error("Unable to create perpetual task for app manifest: {}", appManifestId, e);
    }
  }

  public void deletePerpetualTask(String perpetualTaskId, String appManifestId, String accountId) {
    if (!applicationManifestService.detachPerpetualTask(perpetualTaskId, accountId)) {
      log.error("Unable to detach perpetual task {} for application manifest {}", perpetualTaskId, appManifestId);
    }
    if (!perpetualTaskService.deleteTask(accountId, perpetualTaskId)) {
      log.error("Unable to delete perpetual task {} for application manifest {}", perpetualTaskId, appManifestId);
    }
    log.info("Successfully deleted perpetual task {}", perpetualTaskId);
  }

  public void resetPerpetualTask(ApplicationManifest appManifest) {
    try (AutoLogContext ignore1 = new AccountLogContext(appManifest.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ApplicationManifestLogContext(appManifest.getUuid(), OVERRIDE_ERROR)) {
      if (!perpetualTaskService.resetTask(appManifest.getAccountId(), appManifest.getPerpetualTaskId(), null)) {
        log.error("Unable to reset manifest collection perpetual task: {} ", appManifest.getPerpetualTaskId());
      }
    }
  }
}
