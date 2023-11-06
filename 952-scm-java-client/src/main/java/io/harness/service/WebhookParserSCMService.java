/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.HeaderConfig;
import io.harness.beans.PRWebhookEvent;
import io.harness.beans.WebhookPayload;
import io.harness.product.ci.scm.proto.GitProvider;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequestHook;

import java.util.List;

@OwnedBy(DX)
public interface WebhookParserSCMService {
  ParseWebhookResponse parseWebhookUsingSCMAPI(List<HeaderConfig> headers, String payload);

  WebhookPayload parseWebhookPayload(ParseWebhookResponse parseWebhookResponse);

  PRWebhookEvent convertPRWebhookEvent(PullRequestHook prHook);

  GitProvider obtainWebhookSource(List<HeaderConfig> headers);
}
