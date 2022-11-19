/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepNode;
import io.harness.cdng.commons.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(CDP)
public class AzureCreateARMResourceStepPlanCreator extends CDPMSStepPlanCreatorV2<AzureCreateARMResourceStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE);
  }

  @Override
  public Class<AzureCreateARMResourceStepNode> getFieldClass() {
    return AzureCreateARMResourceStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, AzureCreateARMResourceStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
