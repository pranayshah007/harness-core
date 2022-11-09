/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.update;

import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class ServiceNowUpdateStepPlanCreator extends PMSStepPlanCreatorV2<ServiceNowUpdateStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVICENOW_UPDATE);
  }

  @Override
  public Class<ServiceNowUpdateStepNode> getFieldClass() {
    return ServiceNowUpdateStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ServiceNowUpdateStepNode stepElement) {
    validateServiceNowTemplate(stepElement.getServiceNowUpdateStepInfo());
    return super.createPlanForField(ctx, stepElement);
  }

  protected void validateServiceNowTemplate(ServiceNowUpdateStepInfo serviceNowUpdateStepInfo) {
    ServiceNowUpdateSpecParameters specParameters =
        (ServiceNowUpdateSpecParameters) serviceNowUpdateStepInfo.getSpecParameters();
    if (specParameters.getUseServiceNowTemplate().getValue()) {
      if (ParameterField.isNull(specParameters.getTemplateName())
          || StringUtils.isBlank(specParameters.getTemplateName().getValue())) {
        throw new InvalidRequestException(
            String.format("Missing template name when updating ticket from ServiceNow template in %s step",
                serviceNowUpdateStepInfo.getStepType().getType()));
      }
    }
  }
}
