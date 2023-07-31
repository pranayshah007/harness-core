/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest.BambooArtifactDelegateRequestBuilder;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class BambooBuildStep extends CdTaskExecutable<ArtifactTaskResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.BAMBOO_BUILD).setStepCategory(StepCategory.STEP).build();
  public String COMMAND_UNIT = "Execute";

  @Inject private BambooBuildStepHelperService bambooBuildStepHelperService;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    BambooBuildSpecParameters specParameters = (BambooBuildSpecParameters) stepParameters.getSpec();
    String connectorRef = specParameters.getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    List<EntityDetail> entityDetailList = new ArrayList<>();
    entityDetailList.add(entityDetail);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<ArtifactTaskResponse> responseDataSupplier)
      throws Exception {
    try {
      return bambooBuildStepHelperService.prepareStepResponse(responseDataSupplier);
    } finally {
      // Closing the log stream.
      closeLogStream(ambiance);
    }
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    // Creating the log stream once and will close at the end of the task.
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(COMMAND_UNIT);
    BambooBuildSpecParameters specParameters = (BambooBuildSpecParameters) stepParameters.getSpec();
    BambooArtifactDelegateRequestBuilder paramBuilder =
        BambooArtifactDelegateRequest.builder()
            .connectorRef(specParameters.getConnectorRef().getValue())
            .planKey(specParameters.getPlanName().getValue())
            .parameterEntries(BambooBuildStepUtils.processBambooFieldsInParameters(specParameters.getFields()))
            .delegateSelectors(
                StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()));

    return bambooBuildStepHelperService.prepareTaskRequest(paramBuilder, ambiance,
        specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(),
        "Bamboo Task: Create Bamboo Build Task",
        TaskSelectorYaml.toTaskSelector(specParameters.getDelegateSelectors()));
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }
}
