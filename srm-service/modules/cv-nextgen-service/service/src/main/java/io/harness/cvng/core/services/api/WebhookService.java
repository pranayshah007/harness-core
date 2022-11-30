/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.PagerDutyWebhookEvent;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.PagerDutyWebhook;
import io.harness.cvng.core.entities.Webhook;

public interface WebhookService {
  void createPagerdutyWebhook(
      MonitoredServiceParams monitoredServiceParams, String token, String webhookId, String changeSourceId);

  PagerDutyWebhook getPagerdutyWebhook(ProjectParams projectParams, String changeSourceId);

  void deleteWebhook(Webhook webhook);

  void handlePagerDutyWebhook(String token, PagerDutyWebhookEvent payload);
}
