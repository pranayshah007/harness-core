/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.capability.CapabilitySubjectPermission.PermissionResult;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BatchCapabilityCheckTask extends AbstractDelegateRunnableTask {
  @Inject ProtoCapabilityCheckFactory capabilityCheckFactory;

  public BatchCapabilityCheckTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("deprecated");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    List<CapabilityCheckDetails> capabilityCheckDetailsResultsList = new ArrayList<>();

    List<CapabilityCheckDetails> capabilityCheckDetailsList =
        ((BatchCapabilityCheckTaskParameters) parameters).getCapabilityCheckDetailsList();

    for (CapabilityCheckDetails capabilityCheckDetails : capabilityCheckDetailsList) {
      ProtoCapabilityCheck capabilityCheck =
          capabilityCheckFactory.obtainCapabilityCheck(capabilityCheckDetails.getCapabilityParameters());

      if (capabilityCheck == null) {
        log.error("Unknown capability type: {}", capabilityCheckDetails.getCapabilityType());
        capabilityCheckDetailsResultsList.add(
            capabilityCheckDetails.toBuilder().permissionResult(PermissionResult.UNCHECKED).build());
        continue;
      }

      CapabilitySubjectPermission checkResult =
          capabilityCheck.performCapabilityCheckWithProto(capabilityCheckDetails.getCapabilityParameters());
      capabilityCheckDetailsResultsList.add(
          capabilityCheckDetails.toBuilder().permissionResult(checkResult.getPermissionResult()).build());
    }

    return BatchCapabilityCheckTaskResponse.builder()
        .capabilityCheckDetailsList(capabilityCheckDetailsResultsList)
        .build();
  }
}
