/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure.variablecreator;

import io.harness.cdng.provision.azure.AzureCreateBPStepNode;
import io.harness.cdng.commons.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

public class AzureCreateBPStepVariableCreator extends GenericStepVariableCreator<AzureCreateBPStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE);
  }

  @Override
  public Class<AzureCreateBPStepNode> getFieldClass() {
    return AzureCreateBPStepNode.class;
  }
}
