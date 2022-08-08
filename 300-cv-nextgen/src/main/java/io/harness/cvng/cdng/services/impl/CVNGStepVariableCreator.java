/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.core.beans.CVVerifyStepNode;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CVNGStepVariableCreator extends GenericStepVariableCreator<CVVerifyStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return new HashSet<>(Arrays.asList(CVNGStepType.CVNG_VERIFY.getDisplayName()));
  }

  public Class<CVVerifyStepNode> getFieldClass() {
    return CVVerifyStepNode.class;
  }
}
