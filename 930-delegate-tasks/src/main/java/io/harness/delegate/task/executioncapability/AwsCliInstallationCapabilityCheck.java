package io.harness.delegate.task.executioncapability;

import static io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import static io.harness.k8s.kubectl.Utils.executeCommand;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.delegate.beans.executioncapability.AwsCliInstallationCapability;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;

public class AwsCliInstallationCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  private final String awsVersionCommand = "aws --version";

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability executionCapability) {
    AwsCliInstallationCapability awsCliInstallationCapability = (AwsCliInstallationCapability) executionCapability;

    return CapabilityResponse.builder()
        .validated(executeCommand(awsVersionCommand, 2))
        .delegateCapability(awsCliInstallationCapability)
        .build();
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();

    return builder
        .permissionResult(executeCommand(awsVersionCommand, 2) ? CapabilitySubjectPermission.PermissionResult.ALLOWED
                                                               : CapabilitySubjectPermission.PermissionResult.DENIED)
        .build();
  }
}
