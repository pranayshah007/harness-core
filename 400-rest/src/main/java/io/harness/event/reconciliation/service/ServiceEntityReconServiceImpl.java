/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import io.harness.event.reconciliation.ReconciliationStatus;

import software.wings.search.framework.TimeScaleEntity;

public class ServiceEntityReconServiceImpl implements LookerEntityReconService {
  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, TimeScaleEntity sourceEntityClass) {
    return null;
  }
}
