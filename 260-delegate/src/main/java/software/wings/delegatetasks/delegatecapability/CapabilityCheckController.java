/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.delegatecapability;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.delegate.beans.CapabilityProtoConverter;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.delegate.task.executioncapability.ProtoCapabilityCheck;
import io.harness.delegate.task.executioncapability.ProtoCapabilityCheckFactory;

import software.wings.delegatetasks.validation.AbstractDelegateValidateTask;
import software.wings.delegatetasks.validation.DelegateConnectionResult;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CapabilityCheckController extends AbstractDelegateValidateTask {
  @Inject CapabilityCheckFactory capabilityCheckFactory;
  @Inject ProtoCapabilityCheckFactory protoCapabilityCheckFactory;

  public CapabilityCheckController(String delegateId, DelegateTaskPackage delegateTaskPackage,
      Consumer<List<DelegateConnectionResult>> postExecute) {
    super(delegateId, delegateTaskPackage, postExecute);
  }

  @Override
  public List<DelegateConnectionResult> validate() {
    List<CapabilityResponse> checkResponses = new ArrayList<>();
    try {
      List<ExecutionCapability> executionCapabilities = getExecutionCapabilities();

      if (isEmpty(executionCapabilities)) {
        executionCapabilities =
            Arrays.stream(getParameters())
                .filter(param -> param instanceof ExecutionCapabilityDemander)
                .flatMap(
                    param -> ((ExecutionCapabilityDemander) param).fetchRequiredExecutionCapabilities(null).stream())
                .collect(toList());
      }

      executionCapabilities.forEach(delegateCapability -> {
        log.info("Checking Capability: " + delegateCapability.toString());
        CapabilityCheck capabilityCheck =
            capabilityCheckFactory.obtainCapabilityCheck(delegateCapability.getCapabilityType());
        if (capabilityCheck == null) {
          log.error("Unknown capability type: {}", delegateCapability.getCapabilityType());
          return;
        }

        CapabilityResponse capabilityResponse = capabilityCheck.performCapabilityCheck(delegateCapability);

        try {
          CapabilityParameters parameters = CapabilityProtoConverter.toProto(delegateCapability);
          ProtoCapabilityCheck protoCheck = protoCapabilityCheckFactory.obtainCapabilityCheck(parameters);
          if (CapabilityProtoConverter.shouldCompareResults(parameters)) {
            CapabilitySubjectPermission permission = protoCheck.performCapabilityCheckWithProto(parameters);
            if (CapabilityProtoConverter.hasDivergingResults(capabilityResponse, permission)) {
              log.warn("Diverging capabilities: " + delegateCapability.toString() + " -vs- " + parameters);
            } else {
              log.info("Proto/execution capability have the same result: " + parameters);
            }
          }
        } catch (Exception e) {
          log.warn("Comparison failing for capability : " + delegateCapability.toString() + "; caused by: " + e);
        }

        checkResponses.add(capabilityResponse);
      });

    } catch (RuntimeException exception) {
      log.error("Exception while evaluating capabilities", exception);
      return null;
    }

    return convertResponsesIntoDelegateConnectionResults(checkResponses);
  }

  @Override
  public List<String> getCriteria() {
    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities();

    if (isEmpty(executionCapabilities)) {
      executionCapabilities =
          Arrays.stream(getParameters())
              .filter(param -> param instanceof ExecutionCapabilityDemander)
              .flatMap(param -> ((ExecutionCapabilityDemander) param).fetchRequiredExecutionCapabilities(null).stream())
              .collect(toList());
    }

    return executionCapabilities.stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList());
  }

  // TODO: remove this method once manager is updated to receive CapabilityResponse
  // Manager expects DelegateConnectionResult. This is to be deprecated in future,
  // So we receive output with new data structure "CapabilityResponse"
  // and convert in into something manager understands for now
  private List<DelegateConnectionResult> convertResponsesIntoDelegateConnectionResults(
      List<CapabilityResponse> checkResponses) {
    List<DelegateConnectionResult> delegateConnectionResults = new ArrayList<>();

    if (isNotEmpty(checkResponses)) {
      checkResponses.forEach(checkResponse -> {
        delegateConnectionResults.add(DelegateConnectionResult.builder()
                                          .validated(checkResponse.isValidated())
                                          .criteria(checkResponse.getDelegateCapability().fetchCapabilityBasis())
                                          .build());
      });
    }

    return delegateConnectionResults;
  }
}
