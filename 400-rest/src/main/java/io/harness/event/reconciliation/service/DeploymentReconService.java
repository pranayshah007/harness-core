/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import io.harness.event.reconciliation.ReconciliationStatus;

import software.wings.search.framework.ExecutionEntity;

import java.util.Map;

public interface DeploymentReconService {
  ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, ExecutionEntity executionEntity);
  long getWFExecCountFromMongoDB(String accountId, long durationStartTs, long durationEndTs);
  boolean isStatusMismatchedAndUpdated(Map<String, String> tsdbRunningWFs);
  void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs);
}
