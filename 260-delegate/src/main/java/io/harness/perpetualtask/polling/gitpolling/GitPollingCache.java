/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.gitpolling;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitpolling.github.GitPollingWebhookData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@OwnedBy(HarnessTeam.CDC)
public class GitPollingCache {
  Set<String> publishedWebhookDeliveryIds;
  Set<String> unpublishedWebhookDeliveryIds;
  Set<String> toBeDeletedWebookDeliveryIds;
  List<GitPollingWebhookData> unpublishedWebhooks;
  @NonFinal @Setter boolean firstCollectionOnDelegate;

  public GitPollingCache() {
    this.publishedWebhookDeliveryIds = new HashSet<>();
    this.unpublishedWebhookDeliveryIds = new HashSet<>();
    this.toBeDeletedWebookDeliveryIds = new HashSet<>();
    this.unpublishedWebhooks = new ArrayList<>();
    this.firstCollectionOnDelegate = true;
  }

  public boolean needsToPublish() {
    return !unpublishedWebhooks.isEmpty() || !toBeDeletedWebookDeliveryIds.isEmpty();
  }

  public void populateCache(List<GitPollingWebhookData> gitHubPollingWebhookEventDeliveries) {
    if (isEmpty(gitHubPollingWebhookEventDeliveries)) {
      return;
    }

    Set<String> newKeys = new HashSet<>();
    for (GitPollingWebhookData response : gitHubPollingWebhookEventDeliveries) {
      String webhookId = response.getDeliveryId();
      newKeys.add(webhookId);
      if (!publishedWebhookDeliveryIds.contains(webhookId)) {
        unpublishedWebhooks.add(response);
        unpublishedWebhookDeliveryIds.add(webhookId);
      }
    }

    for (String key : publishedWebhookDeliveryIds) {
      if (!newKeys.contains(key)) {
        toBeDeletedWebookDeliveryIds.add(key);
      }
    }
  }

  public void clearUnpublishedWebhooks(Collection<GitPollingWebhookData> webhooks) {
    if (isEmpty(webhooks)) {
      return;
    }

    Set<String> webhookIds = webhooks.stream().map(GitPollingWebhookData::getDeliveryId).collect(Collectors.toSet());
    this.publishedWebhookDeliveryIds.addAll(webhookIds);
    this.unpublishedWebhookDeliveryIds.removeAll(webhookIds);
    this.unpublishedWebhooks.removeAll(webhooks);
  }

  public void removeDeletedWebhookIds(Collection<String> deletedIds) {
    if (isEmpty(deletedIds)) {
      return;
    }

    publishedWebhookDeliveryIds.removeAll(deletedIds);
    toBeDeletedWebookDeliveryIds.removeAll(deletedIds);
  }
}
