/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.jira.create;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.engine.executions.step.StepExecutionEntityService;
import io.harness.execution.step.jira.create.JiraCreateStepExecutionDetails;
import io.harness.execution.step.jira.create.JiraCreateStepExecutionDetails.JiraCreateStepExecutionDetailsBuilder;
import io.harness.jira.JiraActionNG;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.executables.PipelineTaskExecutable;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.steps.jira.JiraStepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class JiraCreateStep extends PipelineTaskExecutable<JiraTaskNGResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.JIRA_CREATE_STEP_TYPE;

  @Inject private JiraStepHelperService jiraStepHelperService;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private StepExecutionEntityService stepExecutionEntityService;
  @Inject @Named("DashboardExecutorService") ExecutorService dashboardExecutorService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    JiraCreateSpecParameters specParameters = (JiraCreateSpecParameters) stepParameters.getSpec();
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
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    JiraCreateSpecParameters specParameters = (JiraCreateSpecParameters) stepParameters.getSpec();
    JiraTaskNGParametersBuilder paramsBuilder =
        JiraTaskNGParameters.builder()
            .action(JiraActionNG.CREATE_ISSUE)
            .projectKey(specParameters.getProjectKey().getValue())
            .issueType(specParameters.getIssueType().getValue())
            .newMetadata(
                pmsFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.SPG_USE_NEW_METADATA))
            .delegateSelectors(
                StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()))
            .fields(JiraStepUtils.processJiraFieldsInParameters(specParameters.getFields()));
    return jiraStepHelperService.prepareTaskRequest(paramsBuilder, ambiance,
        specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(), "Jira Task: Create Issue");
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<JiraTaskNGResponse> responseSupplier) throws Exception {
    dashboardExecutorService.submit(
        ()
            -> stepExecutionEntityService.updateStepExecutionEntity(ambiance, null,
                createJiraCreateStepExecutionDetailsFromResponse(ambiance, responseSupplier), stepParameters.getName(),
                Status.RUNNING));
    return jiraStepHelperService.prepareStepResponse(responseSupplier);
  }

  private JiraCreateStepExecutionDetails createJiraCreateStepExecutionDetailsFromResponse(
      Ambiance ambiance, ThrowingSupplier<JiraTaskNGResponse> responseSupplier) {
    try {
      JiraTaskNGResponse taskResponse = responseSupplier.get();
      if (taskResponse != null && taskResponse.getIssue() != null) {
        JiraCreateStepExecutionDetailsBuilder builder =
            JiraCreateStepExecutionDetails.builder().url(taskResponse.getIssue().getUrl());
        if (taskResponse.getIssue().getFields().containsKey("Status")) {
          builder.ticketStatus(taskResponse.getIssue().getFields().get("Status").toString());
        }
        return builder.build();
      }
    } catch (Exception ex) {
      log.error(
          String.format(
              "Unable to update step execution entity, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, planExecutionId: %s, stageExecutionId: %s, stepExecutionId: %s",
              AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance), ambiance.getPlanExecutionId(),
              ambiance.getStageExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance)),
          ex);
    }
    return null;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
