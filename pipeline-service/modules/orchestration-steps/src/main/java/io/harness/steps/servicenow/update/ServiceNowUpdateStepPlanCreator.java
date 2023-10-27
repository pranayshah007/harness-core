/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.update;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.servicenow.beans.ChangeTaskUpdateMultipleSpec;

import com.google.common.collect.Sets;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
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
    validateServiceNowSingleTask(stepElement.getServiceNowUpdateStepInfo());
    validateServiceNowMultipleTask(stepElement.getServiceNowUpdateStepInfo());
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

  protected void validateServiceNowSingleTask(ServiceNowUpdateStepInfo serviceNowUpdateStepInfo) {
    ServiceNowUpdateSpecParameters specParameters =
        (ServiceNowUpdateSpecParameters) serviceNowUpdateStepInfo.getSpecParameters();
    if (specParameters.getUpdateMultiple() == null) {
      if (ParameterField.isBlank(specParameters.getTicketNumber())
          || ParameterField.isNull(specParameters.getTicketNumber())) {
        throw new InvalidRequestException(
            String.format("Missing ticket number when updating ticket from ServiceNow template in %s step",
                serviceNowUpdateStepInfo.getStepType().getType()));
      }
      if (ParameterField.isBlank(specParameters.getTicketType())) {
        throw new InvalidRequestException(
            String.format("Missing ticket type when updating ticket from ServiceNow template in %s step",
                serviceNowUpdateStepInfo.getStepType().getType()));
      }
    }
  }

  protected void validateServiceNowMultipleTask(ServiceNowUpdateStepInfo serviceNowUpdateStepInfo) {
    ServiceNowUpdateSpecParameters specParameters =
        (ServiceNowUpdateSpecParameters) serviceNowUpdateStepInfo.getSpecParameters();
    if (specParameters.getUpdateMultiple() != null) {
      ChangeTaskUpdateMultipleSpec changeTaskSpec =
          (ChangeTaskUpdateMultipleSpec) specParameters.getUpdateMultiple().getSpec();
      if (ParameterField.isBlank(specParameters.getTicketType())) {
        throw new InvalidRequestException(
            String.format("Missing ticketType when updating ticket from ServiceNow template in %s step",
                serviceNowUpdateStepInfo.getStepType().getType()));
      }
      if (ParameterField.isBlank(changeTaskSpec.getChangeRequestNumber())) {
        throw new InvalidRequestException(
            String.format("Missing changeRequestNumber value from ServiceNow updateMultiple ticket in %s step",
                serviceNowUpdateStepInfo.getStepType().getType()));
      }
    }
  }
}
