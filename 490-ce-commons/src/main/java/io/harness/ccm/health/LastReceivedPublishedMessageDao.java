/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.health;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage;
import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage.LastReceivedPublishedMessageKeys;
import io.harness.ccm.commons.entities.batch.LatestClusterInfo;
import io.harness.ccm.commons.entities.batch.LatestClusterInfo.LatestClusterInfoKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
@OwnedBy(CE)
public class LastReceivedPublishedMessageDao {
  private final HPersistence hPersistence;

  @Inject
  public LastReceivedPublishedMessageDao(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  public boolean saveLatestClusterInfo(LatestClusterInfo latestClusterInfo) {
    return hPersistence.save(latestClusterInfo) != null;
  }

  public List<LatestClusterInfo> fetchLatestClusterInfo() {
    return hPersistence.createQuery(LatestClusterInfo.class, excludeAuthority).asList();
  }

  public boolean deleteLatestClusterInfo(LatestClusterInfo latestClusterInfo) {
    Query query = hPersistence.createQuery(LatestClusterInfo.class)
                      .field(LatestClusterInfoKeys.uuid)
                      .equal(latestClusterInfo.getUuid());
    return hPersistence.delete(query);
  }

  public LastReceivedPublishedMessage upsert(String accountId, String identifier) {
    return hPersistence.upsert(hPersistence.createQuery(LastReceivedPublishedMessage.class)
                                   .field(LastReceivedPublishedMessageKeys.accountId)
                                   .equal(accountId)
                                   .field(LastReceivedPublishedMessageKeys.identifier)
                                   .equal(identifier),
        hPersistence.createUpdateOperations(LastReceivedPublishedMessage.class)
            .set(LastReceivedPublishedMessageKeys.accountId, accountId)
            .set(LastReceivedPublishedMessageKeys.identifier, identifier)
            .set(LastReceivedPublishedMessageKeys.lastReceivedAt, Instant.now().toEpochMilli()),
        upsertReturnNewOptions);
  }

  public LastReceivedPublishedMessage get(String accountId, String identifier) {
    return hPersistence.createQuery(LastReceivedPublishedMessage.class)
        .field(LastReceivedPublishedMessageKeys.accountId)
        .equal(accountId)
        .field(LastReceivedPublishedMessageKeys.identifier)
        .equal(identifier)
        .get();
  }

  public Map<String, Long> getLastReceivedTimeForClusters(String accountId, List<String> identifiers) {
    Map<String, Long> lastReceivedTimeMap = new HashMap<>();
    for (LastReceivedPublishedMessage message : hPersistence.createQuery(LastReceivedPublishedMessage.class)
                                                    .field(LastReceivedPublishedMessageKeys.accountId)
                                                    .equal(accountId)
                                                    .field(LastReceivedPublishedMessageKeys.identifier)
                                                    .in(identifiers)
                                                    .project(LastReceivedPublishedMessageKeys.identifier, true)
                                                    .project(LastReceivedPublishedMessageKeys.lastReceivedAt, true)
                                                    .fetch()) {
      lastReceivedTimeMap.put(message.getIdentifier(), message.getLastReceivedAt());
    }
    return lastReceivedTimeMap;
  }

  public LastReceivedPublishedMessage get(String accountId) {
    return hPersistence.createQuery(LastReceivedPublishedMessage.class)
        .field(LastReceivedPublishedMessageKeys.accountId)
        .equal(accountId)
        .get();
  }

  public Instant getFirstEventReceivedTime(String accountId) {
    Query<LastReceivedPublishedMessage> filteredQuery =
        hPersistence.createQuery(LastReceivedPublishedMessage.class)
            .filter(LastReceivedPublishedMessageKeys.accountId, accountId)
            .order(Sort.ascending(LastReceivedPublishedMessageKeys.createdAt));
    LastReceivedPublishedMessage lastReceivedPublishedMessage = filteredQuery.get();
    if (null != lastReceivedPublishedMessage) {
      log.info("First event received time {}", lastReceivedPublishedMessage.getCreatedAt());
      return Instant.ofEpochMilli(lastReceivedPublishedMessage.getCreatedAt()).truncatedTo(ChronoUnit.DAYS);
    }
    return null;
  }
}
