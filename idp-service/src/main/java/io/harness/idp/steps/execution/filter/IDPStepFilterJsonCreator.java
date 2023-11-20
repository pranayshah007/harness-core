/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.steps.execution.filter;

import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.idp.steps.utils.IDPCreatorUtils;

import java.util.Set;

public class IDPStepFilterJsonCreator extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    return IDPCreatorUtils.getSupportedSteps();
  }
}