/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.Category;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.Qualifier;

@OwnedBy(PIPELINE)
public interface PollingItemGenerator {
  PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData);

  default PollingItem.Builder getBaseInitializedPollingItem(NGTriggerEntity ngTriggerEntity) {
    if (ngTriggerEntity.getType() != MANIFEST && ngTriggerEntity.getType() != ARTIFACT) {
      throw new InvalidArgumentsException("Only MANIFEST and ARTIFACT trigger types are supported");
    }

    PollingItem.Builder pollingItem = PollingItem.newBuilder();

    pollingItem.setCategory(ngTriggerEntity.getType() == MANIFEST ? Category.MANIFEST : Category.ARTIFACT)
        .setQualifier(Qualifier.newBuilder()
                          .setAccountId(ngTriggerEntity.getAccountId())
                          .setOrganizationId(ngTriggerEntity.getOrgIdentifier())
                          .setProjectId(ngTriggerEntity.getProjectIdentifier())
                          .build())
        .setSignature(ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getSignature());

    if (null != ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getPollingDocId()) {
      pollingItem.setPollingDocId(
          ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getPollingDocId());
    }
    return pollingItem;
  }
}
