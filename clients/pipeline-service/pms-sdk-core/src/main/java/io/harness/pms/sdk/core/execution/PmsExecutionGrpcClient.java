/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import io.harness.pms.contracts.service.ExecutionSummaryUpdateRequest;
import io.harness.pms.contracts.service.PmsExecutionServiceGrpc.PmsExecutionServiceBlockingStub;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PmsExecutionGrpcClient {
  private final PmsExecutionServiceBlockingStub pmsExecutionServiceBlockingStub;

  @Inject
  public PmsExecutionGrpcClient(PmsExecutionServiceBlockingStub pmsExecutionServiceBlockingStub) {
    this.pmsExecutionServiceBlockingStub = pmsExecutionServiceBlockingStub;
  }

  public void updateExecutionSummary(ExecutionSummaryUpdateRequest request) {
    pmsExecutionServiceBlockingStub.updateExecutionSummary(request);
  }
}
