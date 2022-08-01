/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.utilities;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ssh.CommandStepParameters;
import io.harness.common.ParameterFieldHelper;

import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class SshWinRmUtility {
  public static String getHost(@NotNull CommandStepParameters commandStepParameters) {
    if (commandStepParameters == null) {
      return null;
    }

    return ParameterFieldHelper.getParameterFieldValue(commandStepParameters.getHost());
  }
}
