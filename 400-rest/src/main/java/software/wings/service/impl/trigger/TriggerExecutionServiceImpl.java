/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.TriggerExecution.Status;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.trigger.TriggerExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import javax.validation.executable.ValidateOnExecution;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
public class TriggerExecutionServiceImpl implements TriggerExecutionService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AppService appService;

  @Override
  public TriggerExecution save(TriggerExecution triggerExecution) {
    String accountId = appService.getAccountIdByAppId(triggerExecution.getAppId());
    triggerExecution.setAccountId(accountId);
    return wingsPersistence.saveAndGet(TriggerExecution.class, triggerExecution);
  }

  @Override
  public TriggerExecution get(String appId, String triggerExecutionId) {
    return wingsPersistence.getWithAppId(TriggerExecution.class, appId, triggerExecutionId);
  }

  @Override
  public PageResponse<TriggerExecution> list(PageRequest<TriggerExecution> pageRequest) {
    return wingsPersistence.query(TriggerExecution.class, pageRequest);
  }

  @Override
  public TriggerExecution fetchLastSuccessOrRunningExecution(String appId, String triggerId, String webhookToken) {
    return wingsPersistence.createQuery(TriggerExecution.class)
        .filter(TriggerExecution.APP_ID_KEY2, appId)
        .filter(TriggerExecution.TRIGGER_ID_KEY, triggerId)
        .filter(TriggerExecution.WEBHOOK_TOKEN_KEY, webhookToken)
        .field(WorkflowExecutionKeys.status)
        .in(EnumSet.<TriggerExecution.Status>of(Status.RUNNING, Status.SUCCESS))
        .order("-createdAt")
        .get();
  }

  @Override
  public void updateStatus(String appId, String triggerExecutionId, Status status, String message) {
    Query<TriggerExecution> query = wingsPersistence.createQuery(TriggerExecution.class)
                                        .filter("appId", appId)
                                        .filter(TriggerExecution.ID_KEY2, triggerExecutionId);

    UpdateOperations<TriggerExecution> updateOps =
        wingsPersistence.createUpdateOperations(TriggerExecution.class).set("status", status).set("message", message);
    wingsPersistence.update(query, updateOps);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(TriggerExecution.class).filter(ACCOUNT_ID_KEY2, accountId));
  }
}
