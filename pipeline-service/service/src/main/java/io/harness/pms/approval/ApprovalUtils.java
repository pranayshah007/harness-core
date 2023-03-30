/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.approval.step.ApprovalProgressData;
import io.harness.waiter.WaitNotifyEngine;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ApprovalUtils {
  public static void sendTaskIdProgressUpdate(String taskId, String instanceId, WaitNotifyEngine waitNotifyEngine) {
    if (isNotBlank(taskId)) {
      try {
        // Sends approval progress update to update task id to latest delegate task id
        waitNotifyEngine.progressOn(instanceId, ApprovalProgressData.builder().latestDelegateTaskId(taskId).build());
      } catch (Exception ex) {
        // log and ignore the error occurred while progress update
        log.warn("Error sending progress update for taskId {} while polling", taskId, ex);
      }
    }
  }
}
