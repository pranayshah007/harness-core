package io.harness.delegate.task.executioncapability;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.delegate.beans.ci.vm.runner.PoolOwnerStepResponse;
import io.harness.delegate.beans.executioncapability.CIVmConnectionCapability;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;

import java.util.HashMap;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
public class CIVmConnectionCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Inject HttpHelper httpHelper;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    CIVmConnectionCapability connectionCapabiilty = (CIVmConnectionCapability) delegateCapability;
    boolean isOwner = isPoolOwner(connectionCapabiilty.getPoolId());
    log.info(String.format("vm capability check performed, result is %s", isOwner));
    return CapabilityResponse.builder().delegateCapability(delegateCapability).validated(isOwner).build();
  }

  private boolean isPoolOwner(String poolId) {
    HashMap<String, String> params = new HashMap<>();
    params.put("pool_id", poolId);
    Response<PoolOwnerStepResponse> response = httpHelper.isPoolOwner(params);
    boolean isOwner = false;
    if (response.isSuccessful()) {
      isOwner = response.body().isOwner();
    }
    return isOwner;
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();

    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.CI_VM_PARAMETERS) {
      return builder.permissionResult(CapabilitySubjectPermission.PermissionResult.DENIED).build();
    }
    boolean isOwner = isPoolOwner(parameters.getCiVmParameters().getPoolId());
    log.info(String.format("vm capability check performed, result is %s", isOwner));

    return builder
        .permissionResult(isOwner
                ? CapabilitySubjectPermission.PermissionResult.ALLOWED
                : CapabilitySubjectPermission.PermissionResult.DENIED)
        .build();
  }
}