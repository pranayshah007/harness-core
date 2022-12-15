/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.core.beans.CVVerifyStepNode;

import com.google.common.collect.Sets;
import java.util.Set;

public class CVNGPlanCreatorV2 extends CVNGAbstractPlanCreatorV2<CVVerifyStepNode> {
  public static final Set<String> CVNG_SUPPORTED_TYPES = Sets.newHashSet(CVNGStepType.CVNG_VERIFY.getDisplayName());

  @Override
  public Set<String> getSupportedStepTypes() {
    return CVNG_SUPPORTED_TYPES;
  }

  @Override
  public Class<CVVerifyStepNode> getFieldClass() {
    return CVVerifyStepNode.class;
  }
}
