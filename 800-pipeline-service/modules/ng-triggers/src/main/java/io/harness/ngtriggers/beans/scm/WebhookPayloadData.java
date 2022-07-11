/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.scm;

import io.harness.beans.Repository;
import io.harness.beans.WebhookEvent;
import io.harness.beans.WebhookGitUser;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class WebhookPayloadData {
  WebhookGitUser webhookGitUser;
  Repository repository;
  WebhookEvent webhookEvent;
  TriggerWebhookEvent originalEvent;
  ParseWebhookResponse parseWebhookResponse;
}
