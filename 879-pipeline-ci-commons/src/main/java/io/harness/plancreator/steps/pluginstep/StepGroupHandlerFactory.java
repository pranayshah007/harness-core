/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import com.google.inject.Inject;

public class StepGroupHandlerFactory {
  @Inject K8sStepGroupHandler k8sStepGroupHandler;

  public StepGroupInfraHandler getHandler(StepGroupInfra.Type type) {
    if (StepGroupInfra.Type.KUBERNETES_DIRECT.equals(type)) {
      return k8sStepGroupHandler;
    }
    return null;
  }
}
