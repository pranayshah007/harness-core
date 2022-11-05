/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.communication;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.ccm.communication.entities.CESlackWebhook.CESlackWebhookKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;

@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public class CESlackWebhookDao {
  @Inject private HPersistence persistence;

  public CESlackWebhook upsert(CESlackWebhook slackWebhook) {
    Query<CESlackWebhook> query =
        persistence.createQuery(CESlackWebhook.class).filter(CESlackWebhookKeys.accountId, slackWebhook.getAccountId());
    UpdateOperations<CESlackWebhook> updateOperations = persistence.createUpdateOperations(CESlackWebhook.class);
    if (null != slackWebhook.getWebhookUrl()) {
      updateOperations.set(CESlackWebhookKeys.webhookUrl, slackWebhook.getWebhookUrl());
      updateOperations.set(CESlackWebhookKeys.sendCostReport, slackWebhook.isSendCostReport());
      updateOperations.set(CESlackWebhookKeys.sendAnomalyAlerts, slackWebhook.isSendAnomalyAlerts());
    }
    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  public CESlackWebhook getByAccountId(String accountId) {
    return persistence.createQuery(CESlackWebhook.class, excludeValidate)
        .filter(CESlackWebhookKeys.accountId, accountId)
        .get();
  }
}
