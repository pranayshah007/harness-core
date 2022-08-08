/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ACCESS_DENIED;
import static io.harness.exception.WingsException.USER;

import static software.wings.security.AuthRuleFilter.getAllowedAppIds;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.WorkflowType;
import io.harness.exception.TriggerException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Environment;
import software.wings.beans.User;
import software.wings.beans.trigger.Trigger;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EnvironmentService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@OwnedBy(CDC)
@Singleton
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class TriggerAuthHandler {
  @Inject private AuthHandler authHandler;
  @Inject private DeploymentAuthHandler deploymentAuthHandler;
  @Inject private EnvironmentService environmentService;
  @Inject private AuthService authService;
  @Inject private FeatureFlagService featureFlagService;

  void authorizeEnvironment(Trigger trigger, String envId) {
    String appId = trigger.getAppId();
    if (ManagerExpressionEvaluator.matchesVariablePattern(envId)) {
      try {
        authHandler.authorizeAccountPermission(
            asList(new PermissionAttribute(PermissionType.ACCOUNT_MANAGEMENT, Action.READ)));
      } catch (WingsException ex) {
        throw new TriggerException(
            "User not authorized: Only members of the Account Administrator user group can create or update Triggers with parameterized variables.",
            USER);
      }
    } else {
      // Check if environment exist by envId
      Environment environment = environmentService.get(appId, envId);
      if (environment != null) {
        try {
          if (WorkflowType.ORCHESTRATION == trigger.getWorkflowType()) {
            authService.checkIfUserAllowedToDeployWorkflowToEnv(appId, envId);
          } else {
            authService.checkIfUserAllowedToDeployPipelineToEnv(appId, envId);
            if (featureFlagService.isEnabled(PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION, trigger.getAccountId())) {
              deploymentAuthHandler.authorizeExecutableDeployableInEnv(Collections.singleton(envId), appId,
                  trigger.getPipelineId(), ExecutableElementsFilter.FilterType.PIPELINE);
            }
          }
        } catch (WingsException ex) {
          throw new TriggerException(
              "User does not have deployment execution permission on environment. [" + environment.getName() + "]",
              USER);
        }
      } else {
        // either environment does not exist or user give some random name.. then check account level permission
        try {
          authHandler.authorizeAccountPermission(
              asList(new PermissionAttribute(PermissionType.ACCOUNT_MANAGEMENT, Action.READ)));
        } catch (WingsException ex) {
          throw new TriggerException(
              "User not authorized: Only members of the Account Administrator user group can create or update Triggers with parameterized variables",
              USER);
        }
      }
    }
  }

  void authorizeWorkflowOrPipeline(String appId, String workflowOrPipelineId, boolean existing) {
    if (isEmpty(workflowOrPipelineId) && existing) {
      return;
    }
    deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, workflowOrPipelineId);
  }

  public void authorizeAppAccess(List<String> appIds, String accountId) {
    User user = UserThreadLocal.get();
    UserPermissionInfo userPermissionInfo = null;
    if (user.getUserRequestContext() != null && user.getUserRequestContext().getUserPermissionInfo() != null) {
      userPermissionInfo = user.getUserRequestContext().getUserPermissionInfo();
    } else {
      userPermissionInfo = authService.getUserPermissionInfo(accountId, user, false);
    }
    Set<String> allowedAppIds = getAllowedAppIds(userPermissionInfo);
    if (!allowedAppIds.containsAll(appIds)) {
      throw new UnauthorizedException("User Not authorized", ACCESS_DENIED, USER);
    }
  }
}
