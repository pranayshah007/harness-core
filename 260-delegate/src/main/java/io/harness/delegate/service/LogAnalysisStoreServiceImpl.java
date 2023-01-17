/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.managerclient.VerificationServiceClient;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.services.LogAnalysisStoreService;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class LogAnalysisStoreServiceImpl implements LogAnalysisStoreService {
  @Inject private VerificationServiceClient verificationServiceClient;

  @Override
  public boolean save(DelegateStateType stateType, String accountId, String appId, String cvConfigId,
      String stateExecutionId, String workflowId, String workflowExecutionId, String serviceId, String delegateTaskId,
      List<LogElement> logs) {
    try {
      switch (stateType) {
        case SPLUNKV2:
          return execute(
              verificationServiceClient.saveLogs(accountId, appId, cvConfigId, stateExecutionId, workflowId,
                  workflowExecutionId, serviceId, ClusterLevel.L2, delegateTaskId, DelegateStateType.SPLUNKV2, logs))
              .getResource();
        case SUMO:
        case ELK:
        case LOGZ:
        case LOG_VERIFICATION:
        case BUG_SNAG:
        case DATA_DOG_LOG:
        case STACK_DRIVER_LOG:
        case SCALYR:
          return execute(verificationServiceClient.saveLogs(accountId, appId, cvConfigId, stateExecutionId, workflowId,
                             workflowExecutionId, serviceId, ClusterLevel.L0, delegateTaskId, stateType, logs))
              .getResource();
        default:
          throw new IllegalStateException("Invalid state: " + stateType);
      }
    } catch (Exception ex) {
      log.error("Exception while saving log data for stateExecutionId: {}", stateExecutionId, ex);
      return false;
    }
  }
}
