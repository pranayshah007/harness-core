/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.delegate.beans.ci.vm.runner.PoolOwnerStepResponse;
import io.harness.delegate.beans.executioncapability.CIVmConnectionCapability;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.citasks.vm.helper.HttpHelper;

import com.google.inject.Inject;
import retrofit2.Response;

public class CIVmConnectionCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Inject HttpHelper httpHelper;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    CIVmConnectionCapability connectionCapabiilty = (CIVmConnectionCapability) delegateCapability;
    boolean isOwner = isPoolOwner(connectionCapabiilty.getPoolId(), connectionCapabiilty.getStageRuntimeId());
    return CapabilityResponse.builder().delegateCapability(delegateCapability).validated(isOwner).build();
  }

  private boolean isPoolOwner(String poolId, String stageRuntimeId) {
    if (isNotEmpty(stageRuntimeId)) {
      return httpHelper.isPoolOwnerWithStageIdRetries(poolId, stageRuntimeId);
    }
    Response<PoolOwnerStepResponse> response = httpHelper.isPoolOwner(poolId);
    boolean isOwner = false;
    if (response.isSuccessful()) {
      isOwner = response.body().isOwner();
    }
    return isOwner;
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();

    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.CI_VM_PARAMETERS) {
      return builder.permissionResult(CapabilitySubjectPermission.PermissionResult.DENIED).build();
    }
    boolean isOwner =
        isPoolOwner(parameters.getCiVmParameters().getPoolId(), parameters.getCiVmParameters().getStageRuntimeId());

    return builder
        .permissionResult(isOwner ? CapabilitySubjectPermission.PermissionResult.ALLOWED
                                  : CapabilitySubjectPermission.PermissionResult.DENIED)
        .build();
  }
}
