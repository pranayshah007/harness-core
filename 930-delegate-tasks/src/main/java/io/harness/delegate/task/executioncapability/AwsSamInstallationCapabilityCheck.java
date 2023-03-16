/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import static io.harness.k8s.kubectl.Utils.executeCommand;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.delegate.beans.executioncapability.AwsSamInstallationCapability;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;

public class AwsSamInstallationCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  private final String samVersionCommand = "sam --version";

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability executionCapability) {
    AwsSamInstallationCapability awsSamInstallationCapability = (AwsSamInstallationCapability) executionCapability;

    return CapabilityResponse.builder()
        .validated(executeCommand(samVersionCommand, 2))
        .delegateCapability(awsSamInstallationCapability)
        .build();
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();

    return builder
        .permissionResult(executeCommand(samVersionCommand, 2) ? CapabilitySubjectPermission.PermissionResult.ALLOWED
                                                               : CapabilitySubjectPermission.PermissionResult.DENIED)
        .build();
  }
}
