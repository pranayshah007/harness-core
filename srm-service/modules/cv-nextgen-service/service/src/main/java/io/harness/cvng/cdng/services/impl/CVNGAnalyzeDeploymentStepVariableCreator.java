/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.core.beans.CVAnalyzeDeploymentStepNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Set;

public class CVNGAnalyzeDeploymentStepVariableCreator extends GenericStepVariableCreator<CVAnalyzeDeploymentStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return CVNGAnalyzeDeploymentPlanCreator.CVNG_SUPPORTED_TYPES;
  }

  public Class<CVAnalyzeDeploymentStepNode> getFieldClass() {
    return CVAnalyzeDeploymentStepNode.class;
  }
}
