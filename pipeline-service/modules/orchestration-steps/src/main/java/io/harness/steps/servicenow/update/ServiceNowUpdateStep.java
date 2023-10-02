/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.update;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.connector.servicenow.ServiceNowConstants.CHANGE_TASK;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.servicenow.ChangeTaskUpdateMultiple;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowUpdateMultipleTaskNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.servicenow.ServiceNowStepUtils;
import io.harness.steps.servicenow.beans.ChangeTaskUpdateMultipleSpec;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(CDC)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowUpdateStep extends PipelineTaskExecutable<ServiceNowTaskNGResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SERVICE_NOW_UPDATE_STEP_TYPE;
  public static final String NULLABLE = "null";

  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceNowStepHelperService serviceNowStepHelperService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ServiceNowUpdateSpecParameters specParameters = (ServiceNowUpdateSpecParameters) stepParameters.getSpec();
    String connectorRef = specParameters.getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    List<EntityDetail> entityDetailList = new ArrayList<>();
    entityDetailList.add(entityDetail);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);

    ServiceNowUpdateSpecParameters specParameters = (ServiceNowUpdateSpecParameters) stepParameters.getSpec();
    ServiceNowUpdateMultipleTaskNode updateMultipleTaskNode = null;
    if (specParameters.getUpdateMultiple() != null) {
      String updateMultipleType = specParameters.getUpdateMultiple().getType().name();
      if (updateMultipleType.equalsIgnoreCase(CHANGE_TASK)) {
        ChangeTaskUpdateMultipleSpec changeTaskSpec =
            (ChangeTaskUpdateMultipleSpec) specParameters.getUpdateMultiple().getSpec();
        if (NULLABLE.equalsIgnoreCase(changeTaskSpec.getChangeRequestNumber().getValue())) {
          throw new InvalidRequestException(
              "Cannot resolve the expression for changeRequestNumber", WingsException.USER);
        }
        if (NULLABLE.equalsIgnoreCase(changeTaskSpec.getChangeTaskType().getValue())) {
          throw new InvalidRequestException("Cannot resolve the expression for changeTaskType", WingsException.USER);
        }
        updateMultipleTaskNode = ServiceNowUpdateMultipleTaskNode.builder()
                                     .type(specParameters.getUpdateMultiple().getType().name())
                                     .spec(ChangeTaskUpdateMultiple.builder()
                                               .changeTaskType(changeTaskSpec.getChangeTaskType().getValue())
                                               .changeRequestNumber(changeTaskSpec.getChangeRequestNumber().getValue())
                                               .build())
                                     .build();
      }
    }

    ParameterField<String> ticketType = specParameters.getTicketType();
    ParameterField<String> ticketNumber = specParameters.getTicketNumber();

    ServiceNowTaskNGParametersBuilder paramsBuilder =
        ServiceNowTaskNGParameters.builder()
            .action(ServiceNowActionNG.UPDATE_TICKET)
            .ticketType(ticketType != null ? ticketType.getValue() : null)
            .ticketNumber(ticketNumber != null ? ticketNumber.getValue() : null)
            .updateMultiple(updateMultipleTaskNode)
            .templateName(specParameters.getTemplateName().getValue())
            .useServiceNowTemplate(specParameters.getUseServiceNowTemplate().getValue())
            .delegateSelectors(
                StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()))
            .fields(ServiceNowStepUtils.processServiceNowFieldsInSpec(specParameters.getFields(), logCallback));
    return serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance,
        specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(),
        "ServiceNow Task: Update Ticket", TaskSelectorYaml.toTaskSelector(specParameters.getDelegateSelectors()));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepBaseParameters stepParameters,
      ThrowingSupplier<ServiceNowTaskNGResponse> responseSupplier) throws Exception {
    try {
      return serviceNowStepHelperService.prepareStepResponse(responseSupplier);
    } finally {
      closeLogStream(ambiance);
    }
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepBaseParameters stepParameters, TaskExecutableResponse executableResponse) {
    closeLogStream(ambiance);
  }

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }
  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }
}
