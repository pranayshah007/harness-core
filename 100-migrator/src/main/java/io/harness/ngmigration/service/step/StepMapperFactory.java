/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import com.google.inject.Inject;

public class StepMapperFactory {
  @Inject ShellScriptStepMapperImpl shellScriptStepMapper;
  @Inject K8sRollingStepMapperImpl k8sRollingStepMapper;

  public StepMapper getStepMapper(String stepType) {
    switch (stepType) {
      case "SHELL_SCRIPT":
        return shellScriptStepMapper;
      case "K8S_DEPLOYMENT_ROLLING":
        return k8sRollingStepMapper;
      default:
        throw new UnsupportedOperationException();
    }
  }
}
