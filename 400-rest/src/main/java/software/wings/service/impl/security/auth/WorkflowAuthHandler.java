/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.User;
import software.wings.beans.trigger.Trigger;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AuthService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class WorkflowAuthHandler {
  @Inject private AuthService authService;

  private void authorize(List<PermissionAttribute> requiredPermissionAttributes, List<String> appIds, String entityId) {
    User user = UserThreadLocal.get();
    if (user != null && user.getUserRequestContext() != null) {
      UserRequestContext userRequestContext = user.getUserRequestContext();
      if (userRequestContext != null) {
        authService.authorize(userRequestContext.getAccountId(), appIds, entityId, user, requiredPermissionAttributes);
      }
    }
  }

  private void authorizeWorkflow(String appId, String entityId, PermissionType permissionType, Action action) {
    List<PermissionAttribute> permissionAttributeList = new ArrayList<>();
    permissionAttributeList.add(new PermissionAttribute(permissionType, action));
    authorize(permissionAttributeList, Collections.singletonList(appId), entityId);
  }

  public void authorizeWorkflowAction(@NotNull String appId, @NotNull Trigger trigger, @NotNull Action action) {
    if (ORCHESTRATION.equals(trigger.getWorkflowType())) {
      authorizeWorkflow(appId, trigger.getWorkflowId(), PermissionType.WORKFLOW, action);
    } else if (PIPELINE.equals(trigger.getWorkflowType())) {
      authorizeWorkflow(appId, trigger.getPipelineId(), PermissionType.PIPELINE, action);
    }
  }
}
