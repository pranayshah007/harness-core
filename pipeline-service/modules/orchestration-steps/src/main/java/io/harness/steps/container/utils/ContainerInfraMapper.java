/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.exception.InvalidArgumentsException;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;

import com.google.common.base.Preconditions;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContainerInfraMapper {
  public static StageInfraDetails.Type toStageInfraType(ContainerStepInfra infra) {
    Preconditions.checkNotNull(infra);
    switch (infra.getType()) {
      case KUBERNETES_DIRECT:
        return StageInfraDetails.Type.K8;
      default:
        throw new InvalidArgumentsException(
            String.format("Infrastructure type %s is not yet supported for container step", infra.getType()));
    }
  }
}
