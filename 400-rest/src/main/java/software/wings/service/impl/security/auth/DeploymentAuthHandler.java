/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.security.PermissionAttribute.Action.ABORT_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_PIPELINE;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW;
import static software.wings.security.PermissionAttribute.Action.EXECUTE_WORKFLOW_ROLLBACK;
import static software.wings.security.PermissionAttribute.PermissionType.ALLOW_DEPLOYMENTS_DURING_FREEZE;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Pipeline;
import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.security.AppPermissionSummary;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by ujjawal on 05/24/20.
 */
@Singleton
@Slf4j
@OwnedBy(PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class DeploymentAuthHandler {
  @Inject private AuthService authService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowService workflowService;
  @Inject private PipelineService pipelineService;
  @Inject private AuthHandler authHandler;
  @Inject private FeatureFlagService featureFlagService;

  private void authorize(List<PermissionAttribute> requiredPermissionAttributes, List<String> appIds, String entityId) {
    User user = UserThreadLocal.get();
    if (user != null && user.getUserRequestContext() != null) {
      UserRequestContext userRequestContext = user.getUserRequestContext();
      if (userRequestContext != null) {
        authService.authorize(userRequestContext.getAccountId(), appIds, entityId, user, requiredPermissionAttributes);
      }
    }
  }

  public void authorize(String appId, String workflowExecutionId) {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    WorkflowExecution workflowExecution =
        workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecutionId);

    if (workflowExecution.getPipelineSummary() != null) {
      String pipelineId = workflowExecution.getPipelineSummary().getPipelineId();
      notNullCheck("Pipeline id is null for execution " + workflowExecutionId, pipelineId);
      permissionAttributeList.add(new PermissionAttribute(DEPLOYMENT, EXECUTE_PIPELINE));
      authorize(permissionAttributeList, asList(appId), pipelineId);
    } else {
      String workflowId = workflowExecution.getWorkflowId();
      notNullCheck("Workflow id is null for execution " + workflowExecutionId, workflowId);
      permissionAttributeList.add(new PermissionAttribute(DEPLOYMENT, EXECUTE_WORKFLOW));
      authorize(permissionAttributeList, asList(appId), workflowId);
    }
    checkPermissionToDeployEnv(appId, workflowExecution);
  }

  public void authorizeRollback(String appId, String workflowExecutionId) {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    WorkflowExecution workflowExecution =
        workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecutionId);
    if (workflowExecution != null) {
      String workflowId = workflowExecution.getWorkflowId();
      notNullCheck("Workflow id is null for execution " + workflowExecutionId, workflowId);
      permissionAttributeList.add(new PermissionAttribute(DEPLOYMENT, EXECUTE_WORKFLOW_ROLLBACK));
      authorize(permissionAttributeList, asList(appId), workflowId);
      authService.checkIfUserAllowedToRollbackWorkflowToEnv(appId, workflowExecution.getEnvId());
    }
  }
  public void authorizeAbortWorkflow(String appId, WorkflowExecution workflowExecution) {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    if (workflowExecution != null) {
      String workflowId = workflowExecution.getWorkflowId();
      notNullCheck("Workflow id is null for execution " + workflowId, workflowId);
      permissionAttributeList.add(new PermissionAttribute(DEPLOYMENT, ABORT_WORKFLOW));
      authorize(permissionAttributeList, asList(appId), workflowId);
      authService.checkIfUserAllowedToAbortWorkflowToEnv(appId, workflowExecution.getEnvId());
    }
  }

  public void authorize(String appId, WorkflowExecution workflowExecution) {
    if (workflowExecution != null && workflowExecution.getWorkflowId() != null) {
      String workflowId = workflowExecution.getWorkflowId();
      List<PermissionAttribute> permissionAttributeList = getPermissionAttributeList(workflowExecution);
      authorize(permissionAttributeList, Collections.singletonList(appId), workflowId);
    }
    checkPermissionToDeployEnv(appId, workflowExecution);
  }

  public void authorizeRollback(String appId, WorkflowExecution workflowExecution) {
    if (workflowExecution != null && workflowExecution.getWorkflowId() != null) {
      String workflowId = workflowExecution.getWorkflowId();
      List<PermissionAttribute> permissionAttributeList =
          Collections.singletonList(new PermissionAttribute(DEPLOYMENT, EXECUTE_WORKFLOW_ROLLBACK));
      authorize(permissionAttributeList, Collections.singletonList(appId), workflowId);
      authService.checkIfUserAllowedToRollbackWorkflowToEnv(appId, workflowExecution.getEnvId());
    }
  }

  private List<PermissionAttribute> getPermissionAttributeList(WorkflowExecution workflowExecution) {
    if (workflowExecution.getWorkflowType() == PIPELINE) {
      return Collections.singletonList(new PermissionAttribute(DEPLOYMENT, EXECUTE_PIPELINE));
    } else {
      return Collections.singletonList(new PermissionAttribute(DEPLOYMENT, EXECUTE_WORKFLOW));
    }
  }

  public void authorizeWithWorkflowExecutionId(String appId, String workflowExecutionId) {
    String[] fields = {WorkflowExecutionKeys.envId, WorkflowExecutionKeys.pipelineSummary,
        WorkflowExecutionKeys.workflowId, WorkflowExecutionKeys.workflowType};
    WorkflowExecution workflowExecution =
        workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId, fields);
    authorize(appId, workflowExecution);
  }

  public void authorizePipelineExecution(String appId, String entityId) {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    permissionAttributeList.add(new PermissionAttribute(DEPLOYMENT, EXECUTE_PIPELINE));
    authorize(permissionAttributeList, Collections.singletonList(appId), entityId);
  }

  public void authorizeExecutableDeployableInEnv(
      Set<String> envIds, String appId, String executableId, String filterType) {
    authService.authorize(envIds, appId,
        AppPermissionSummary.ExecutableElementInfo.builder().entityType(filterType).entityId(executableId).build());
  }

  public void authorizeWorkflowExecution(String appId, String entityId) {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    permissionAttributeList.add(new PermissionAttribute(DEPLOYMENT, EXECUTE_WORKFLOW));
    authorize(permissionAttributeList, Collections.singletonList(appId), entityId);
  }

  public void authorizeWorkflowOrPipelineForExecution(@NotNull String appId, @NotNull String workflowOrPipelineId) {
    Workflow workflow = workflowService.getWorkflow(appId, workflowOrPipelineId);
    if (workflow != null) {
      authorizeWorkflowExecution(appId, workflowOrPipelineId);
      //      if (featureFlagService.isEnabled(PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, workflow.getAccountId())) {
      //        authorizeExecutableDeployableInEnv(
      //                new HashSet<>(workflow.getEnvId()), appId, workflowOrPipelineId,
      //                ExecutableElementsFilter.FilterType.WORKFLOW);
      //      }
    }
    Pipeline pipeline = pipelineService.getPipeline(appId, workflowOrPipelineId);
    if (pipeline != null) {
      authorizePipelineExecution(appId, workflowOrPipelineId);
      if (featureFlagService.isEnabled(PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, pipeline.getAccountId())) {
        Pipeline fullPipeline = pipelineService.readPipeline(appId, workflowOrPipelineId, true);
        authorizeExecutableDeployableInEnv(new HashSet<>(fullPipeline.getEnvIds()), appId, workflowOrPipelineId,
            ExecutableElementsFilter.FilterType.PIPELINE);
      }
    }
    if (workflow == null && pipeline == null) {
      log.error(
          "Provided workflowOrPipelineId for Auth is neither a workflow nor a pipeline, with appId {} and entityId {}",
          appId, workflowOrPipelineId);
      throw new InvalidRequestException(
          "Provided workflowOrPipelineId for authorization is neither a workflow nor a pipeline");
    }
  }

  private void checkPermissionToDeployEnv(String appId, WorkflowExecution workflowExecution) {
    if (workflowExecution != null && StringUtils.isNotEmpty(workflowExecution.getEnvId())) {
      // workflow execution is direct workflow execution and not part of any pipeline
      if (ORCHESTRATION == workflowExecution.getWorkflowType() && workflowExecution.getPipelineSummary() == null) {
        authService.checkIfUserAllowedToDeployWorkflowToEnv(appId, workflowExecution.getEnvId());
      } else {
        authService.checkIfUserAllowedToDeployPipelineToEnv(appId, workflowExecution.getEnvId());
      }
    }
  }

  public void authorizeDeploymentDuringFreeze() {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    permissionAttributeList.add(new PermissionAttribute(ALLOW_DEPLOYMENTS_DURING_FREEZE));
    authHandler.authorizeAccountPermission(permissionAttributeList);
  }
}
