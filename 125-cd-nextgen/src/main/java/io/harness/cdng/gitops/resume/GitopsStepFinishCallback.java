/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.resume;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.gitrestraint.services.GitRestraintInstanceService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitopsprovider.entity.GitRestraintInstance;
import io.harness.springdata.TransactionHelper;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(GITOPS)
@Slf4j
public class GitopsStepFinishCallback implements OldNotifyCallback {
  @Inject private ExecutorService executorService;
  @Inject private GitRestraintInstanceService gitRestraintInstanceService;
  @Inject private TransactionHelper transactionHelper;

  @NonNull private final String releaseEntityId;

  @Builder
  public GitopsStepFinishCallback(@NonNull String releaseEntityId) {
    this.releaseEntityId = releaseEntityId;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    executorService.submit(() -> {
      log.info("Update Active Github Resource constraints");

      final List<GitRestraintInstance> gitRestraintInstances =
          gitRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId);
      log.info("Found {} active resource restraint instances", gitRestraintInstances.size());

      if (EmptyPredicate.isNotEmpty(gitRestraintInstances)) {
        for (GitRestraintInstance ri : gitRestraintInstances) {
          transactionHelper.performTransaction(() -> {
            gitRestraintInstanceService.finishInstance(ri.getUuid());
            gitRestraintInstanceService.updateBlockedConstraints(ri.getResourceUnit());
            return null;
          });
        }
      }
    });
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.info("Gitops Step Finisher Error Callback Received");
  }
}
