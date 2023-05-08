/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateInfoV2;
import io.harness.delegate.beans.DelegateTaskLoggingV2;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskPackageV2;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.DelegateAuth;

import software.wings.beans.TaskType;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/agent/v2/delegates")
@Path("/agent/v2/delegates")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
@BreakDependencyOn("software.wings.service.impl.instance.InstanceHelper")
public class DelegateAgentResourceV2 {
  private DelegateTaskServiceClassic delegateTaskServiceClassic;

  @Inject
  public DelegateAgentResourceV2(DelegateTaskServiceClassic delegateTaskServiceClassic) {
    this.delegateTaskServiceClassic = delegateTaskServiceClassic;
  }

  @DelegateAuth
  @PUT
  @Path("{delegateId}/tasks/{taskId}/acquire")
  @Timed
  @ExceptionMetered
  public DelegateTaskPackageV2 acquireDelegateTaskV2(@PathParam("delegateId") String delegateId,
      @PathParam("taskId") String taskId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateInstanceId") String delegateInstanceId) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(accountId, delegateId, delegateInstanceId, OVERRIDE_ERROR)) {
      DelegateTaskPackage delegateTaskPackage =
          delegateTaskServiceClassic.acquireDelegateTask(accountId, delegateId, taskId, delegateInstanceId);
      // Convert DelegateTaskPackage to DelegateTaskPackageV2
      DelegateInfoV2 delegateInfo = DelegateInfoV2.builder()
                                        .id(delegateTaskPackage.getDelegateId())
                                        .instanceId(delegateTaskPackage.getDelegateInstanceId())
                                        .callbackToken(delegateTaskPackage.getDelegateCallbackToken())
                                        .build();
      DelegateTaskLoggingV2 delegateTaskLoggingV2 =
          DelegateTaskLoggingV2.builder()
              .loggingToken(delegateTaskPackage.getLogStreamingToken())
              .logStreamingAbstractions(delegateTaskPackage.getLogStreamingAbstractions())
              .build();
      TaskType taskType = TaskType.valueOf(delegateTaskPackage.getData().getTaskType());

      return DelegateTaskPackageV2.builder()
          .id(taskId)
          .delegate(delegateInfo)
          .async(delegateTaskPackage.getData().isAsync())
          .executionCapabilities(delegateTaskPackage.getExecutionCapabilities())
          .data(delegateTaskPackage.getData().getParameters()[0])
          .timeout(delegateTaskPackage.getData().getTimeout())
          .encryptionConfigs(delegateTaskPackage.getEncryptionConfigs())
          .secretDetails(delegateTaskPackage.getSecretDetails())
          .delegateTaskLogging(delegateTaskLoggingV2)
          .secrets(delegateTaskPackage.getSecrets())
          .taskType(taskType)
          .build();
    }
  }
}
