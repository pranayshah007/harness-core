/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.executioncapability.CapabilityCheck;

import software.wings.delegatetasks.delegatecapability.CapabilityCheckFactory;
import software.wings.service.impl.CapabilityCheckResponse;
import software.wings.service.impl.PerpetualTaskCapabilityCheckResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class PerpetualTaskCapabilityCheckTask extends AbstractDelegateRunnableTask {
  @Inject CapabilityCheckFactory capabilityCheckFactory;

  public PerpetualTaskCapabilityCheckTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public CapabilityCheckResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public CapabilityCheckResponse run(Object[] parameters) {
    List<CapabilityResponse> checkResponses = new ArrayList<>();

    List<ExecutionCapability> executionCapabilities =
        Arrays.stream(parameters).map(param -> (ExecutionCapability) param).collect(toList());

    if (isEmpty(executionCapabilities)) {
      return PerpetualTaskCapabilityCheckResponse.builder().ableToExecutePerpetualTask(true).build();
    }

    executionCapabilities.forEach(delegateCapability -> {
      log.info("Checking Capability: " + delegateCapability.toString());
      CapabilityCheck capabilityCheck =
          capabilityCheckFactory.obtainCapabilityCheck(delegateCapability.getCapabilityType());

      if (capabilityCheck == null) {
        log.error("Unknown capability type: {}", delegateCapability.getCapabilityType());
        return;
      }

      checkResponses.add(capabilityCheck.performCapabilityCheck(delegateCapability));
    });
    boolean validated = false;
    if (isNotEmpty(checkResponses)) {
      validated = checkResponses.stream().allMatch(CapabilityResponse::isValidated);
    }
    return PerpetualTaskCapabilityCheckResponse.builder().ableToExecutePerpetualTask(validated).build();
  }
}
