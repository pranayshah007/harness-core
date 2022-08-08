/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import io.harness.delegate.task.helm.HelmInstallCmdResponseNG;

import software.wings.helpers.ext.helm.response.HelmInstallCommandResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class HelmCommandResponseMapper {
  public HelmInstallCommandResponse getHelmInstallCommandResponse(HelmClientImpl.HelmCliResponse cliResponse) {
    return HelmInstallCommandResponse.builder()
        .commandExecutionStatus(cliResponse.getCommandExecutionStatus())
        .output(cliResponse.getOutputWithErrorStream())
        .build();
  }

  public HelmInstallCmdResponseNG getHelmInstCmdRespNG(HelmClientImpl.HelmCliResponse cliResponse) {
    return HelmInstallCmdResponseNG.builder()
        .commandExecutionStatus(cliResponse.getCommandExecutionStatus())
        .output(cliResponse.getOutputWithErrorStream())
        .build();
  }
}
