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
import static io.harness.ngtriggers.beans.source.NGTriggerType.MULTI_REGION_ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.Category;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.Qualifier;

@OwnedBy(PIPELINE)
public interface PollingItemGenerator {
  PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData);

  default PollingItem.Builder getBaseInitializedPollingItem(
      NGTriggerEntity ngTriggerEntity, BuildTriggerOpsData buildTriggerOpsData) {
    if (ngTriggerEntity.getType() != MANIFEST && ngTriggerEntity.getType() != ARTIFACT
        && ngTriggerEntity.getType() != WEBHOOK && ngTriggerEntity.getType() != MULTI_REGION_ARTIFACT) {
      throw new InvalidArgumentsException("Only MANIFEST, ARTIFACT and WEBHOOK trigger types are supported");
    }

    PollingItem.Builder pollingItem = PollingItem.newBuilder();

    NGTriggerType type = ngTriggerEntity.getType();
    Category category = null;
    if (type == MANIFEST) {
      category = Category.MANIFEST;
    } else if (type == ARTIFACT || type == MULTI_REGION_ARTIFACT) {
      category = Category.ARTIFACT;
    } else if (type == WEBHOOK) {
      category = Category.GITPOLLING;
    }

    pollingItem.setCategory(category).setQualifier(Qualifier.newBuilder()
                                                       .setAccountId(ngTriggerEntity.getAccountId())
                                                       .setOrganizationId(ngTriggerEntity.getOrgIdentifier())
                                                       .setProjectId(ngTriggerEntity.getProjectIdentifier())
                                                       .build());

    String pollingDocId;
    if (type == MULTI_REGION_ARTIFACT) {
      /* For MultiRegionArtifact triggers, we need to fetch signature and pollingDocId from `buildTriggerOpsData`,
      because the trigger's metadata itself contains a list of BuildMetadata, so we don't know which element of the
      list corresponds to the pollingItem we are generating here. */
      pollingItem.setSignature(buildTriggerOpsData.getBuildMetadata().getPollingConfig().getSignature());
      pollingItem.addAllSignaturesToLock(buildTriggerOpsData.getSignaturesToLock());
      pollingDocId = buildTriggerOpsData.getBuildMetadata().getPollingConfig().getPollingDocId();
    } else {
      pollingItem.setSignature(ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getSignature());
      pollingDocId = ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getPollingDocId();
    }

    if (pollingDocId != null) {
      pollingItem.setPollingDocId(pollingDocId);
    }
    return pollingItem;
  }
}
