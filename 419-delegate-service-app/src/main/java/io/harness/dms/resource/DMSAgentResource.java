package io.harness.dms.resource;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.DMSTaskServiceClassic;

import software.wings.security.annotations.Scope;

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

@Api("/agent/delegates/dms")
@Path("/agent/delegates/dms")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@OwnedBy(DEL)
public class DMSAgentResource {
  private DMSTaskServiceClassic dmsTaskServiceClassic;

  @Inject
  public DMSAgentResource(DMSTaskServiceClassic dmsTaskServiceClassic) {
    this.dmsTaskServiceClassic = dmsTaskServiceClassic;
  }

  @DelegateAuth
  @PUT
  @Produces("application/x-kryo-v2")
  @Path("{delegateId}/tasks/{taskId}/acquire/v2")
  @Timed
  @ExceptionMetered
  public DelegateTaskPackage acquireDelegateTaskV2(@PathParam("delegateId") String delegateId,
      @PathParam("taskId") String taskId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("delegateInstanceId") String delegateInstanceId) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(accountId, delegateId, delegateInstanceId, OVERRIDE_ERROR)) {
      log.info("Received call inside DMS from Delegate");
      return dmsTaskServiceClassic.acquireDelegateTask(accountId, delegateId, taskId, delegateInstanceId);
    }
  }
}
