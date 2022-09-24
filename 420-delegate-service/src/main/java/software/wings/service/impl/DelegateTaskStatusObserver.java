/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.DelegateTaskUsageInsightsEventType;

@OwnedBy(DEL)
public interface DelegateTaskStatusObserver {
  void onTaskAssigned(String accountId, String taskId, String delegateId, long taskTimeout);
  void onTaskCompleted(
      String accountId, String taskId, String delegateId, DelegateTaskUsageInsightsEventType eventType);
}
