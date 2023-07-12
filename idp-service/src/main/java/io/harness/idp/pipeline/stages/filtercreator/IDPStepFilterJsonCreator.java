/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.filtercreator;

import io.harness.filters.GenericStepPMSFilterJsonCreator;
import io.harness.idp.pipeline.stages.utils.IDPCreatorUtils;

import java.util.Set;

public class IDPStepFilterJsonCreator extends GenericStepPMSFilterJsonCreator {
  @Override
  public Set<String> getSupportedStepTypes() {
    return IDPCreatorUtils.getSupportedSteps();
  }
}
