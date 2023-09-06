/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.create;

import static io.harness.annotations.dev.HarnessTeam.CDC;
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
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.steps.servicenow.ServiceNowStepHelperService;
import io.harness.steps.servicenow.ServiceNowStepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(CDC)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowCreateStep extends PipelineTaskExecutable<ServiceNowTaskNGResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SERVICE_NOW_CREATE_STEP_TYPE;

  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceNowStepHelperService serviceNowStepHelperService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ServiceNowCreateSpecParameters specParameters = (ServiceNowCreateSpecParameters) stepParameters.getSpec();
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
    ServiceNowCreateSpecParameters specParameters = (ServiceNowCreateSpecParameters) stepParameters.getSpec();
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);

    ServiceNowTaskNGParametersBuilder paramsBuilder =
        ServiceNowTaskNGParameters.builder()
            .action(ServiceNowActionNG.CREATE_TICKET)
            .ticketType(specParameters.getTicketType().getValue())
            .templateName(specParameters.getTemplateName().getValue())
            .useServiceNowTemplate(specParameters.getUseServiceNowTemplate().getValue())
            .delegateSelectors(
                StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()))
            .fields(ServiceNowStepUtils.processServiceNowFieldsInSpec(specParameters.getFields(), logCallback));
    return serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance,
        specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(),
        "ServiceNow Task: Create Ticket", TaskSelectorYaml.toTaskSelector(specParameters.getDelegateSelectors()));
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
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepBaseParameters stepParameters, TaskExecutableResponse executableResponse) {
    closeLogStream(ambiance);
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }
}
