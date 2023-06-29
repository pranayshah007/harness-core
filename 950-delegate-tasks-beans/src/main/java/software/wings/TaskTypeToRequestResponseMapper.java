/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;

import software.wings.beans.TaskType;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TaskTypeToRequestResponseMapper {
  public static Optional<Class<? extends TaskParameters>> getTaskRequestClass(TaskType taskType) {
    switch (taskType) {
      case DLITE_CI_VM_INITIALIZE_TASK:
        return Optional.of(DliteVmInitializeTaskParams.class);
      case DLITE_CI_VM_EXECUTE_TASK:
        return Optional.of(DliteVmExecuteStepTaskParams.class);
      case DLITE_CI_VM_CLEANUP_TASK:
        return Optional.of(DliteVmCleanupTaskParams.class);
      default:
        return Optional.empty();
    }
  }

  public static Optional<Class<? extends DelegateResponseData>> getTaskResponseClass(TaskType taskType) {
    switch (taskType) {
      case CI_LE_STATUS:
        return Optional.of(StepStatusTaskResponseData.class);
      case DLITE_CI_VM_INITIALIZE_TASK:
      case DLITE_CI_VM_EXECUTE_TASK:
      case DLITE_CI_VM_CLEANUP_TASK:
        return Optional.of(VmTaskExecutionResponse.class);
      default:
        return Optional.empty();
    }
  }
}
