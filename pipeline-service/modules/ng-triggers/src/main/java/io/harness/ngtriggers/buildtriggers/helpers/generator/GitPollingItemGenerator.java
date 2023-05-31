/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGTimeConversionHelper;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.GitPollingPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class GitPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();

    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);

    String connectorRef = buildTriggerOpsData.getTriggerDetails()
                              .getNgTriggerEntity()
                              .getMetadata()
                              .getWebhook()
                              .getGit()
                              .getConnectorIdentifier();

    String webhookId =
        buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity().getTriggerStatus().getWebhookInfo().getWebhookId();
    String repository =
        buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity().getMetadata().getWebhook().getGit().getRepoName();

    int pollInterval = NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(
        buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity().getPollInterval());

    GitPollingPayload.Builder pollingPayloadBuilder =
        GitPollingPayload.newBuilder().setPollInterval(pollInterval).setWebhookId(webhookId);
    if (repository != null) {
      pollingPayloadBuilder.setRepository(repository);
    }

    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setConnectorRef(connectorRef)
                                   .setType(Type.GIT_POLL)
                                   .setGitPollPayload(pollingPayloadBuilder.build())
                                   .build())
        .build();
  }
}
