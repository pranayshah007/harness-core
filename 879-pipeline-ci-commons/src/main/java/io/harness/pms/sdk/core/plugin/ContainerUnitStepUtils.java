/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.beans.yaml.extended.CIShellType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.product.ci.engine.proto.RunStep;
import io.harness.product.ci.engine.proto.ShellType;
import io.harness.product.ci.engine.proto.UnitStep;

import java.util.function.Supplier;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContainerUnitStepUtils {
  public UnitStep getUnitStep(Integer port, String callbackId, String logKey, String identifier, String accountId,
      String stepName, RunStep.Builder runStepBuilder, CIShellType shellType,
      Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier) {
    ShellType protoShellType = ShellType.SH;
    if (shellType == CIShellType.BASH) {
      protoShellType = ShellType.BASH;
    } else if (shellType == CIShellType.PWSH) {
      protoShellType = ShellType.PWSH;
    } else if (shellType == CIShellType.POWERSHELL) {
      protoShellType = ShellType.POWERSHELL;
    } else if (shellType == CIShellType.PYTHON) {
      protoShellType = ShellType.PYTHON;
    }

    runStepBuilder.setShellType(protoShellType);

    return UnitStep.newBuilder()
        .setAccountId(accountId)
        .setContainerPort(port)
        .setId(identifier)
        .setTaskId(callbackId)
        .setCallbackToken(delegateCallbackTokenSupplier.get().getToken())
        .setDisplayName(stepName)
        .setRun(runStepBuilder.build())
        .setLogKey(logKey)
        .build();
  }
}
