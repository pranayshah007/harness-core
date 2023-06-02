/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.mapper.gitpolling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.GitHubPollingInfo;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

@OwnedBy(HarnessTeam.CDP)
public class GitPollingInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return GitHubPollingInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .pollInterval(pollingPayloadData.getGitPollPayload().getPollInterval())
        .webhookId(pollingPayloadData.getGitPollPayload().getWebhookId())
        .repository(pollingPayloadData.getGitPollPayload().getRepository())
        .build();
  }
}
