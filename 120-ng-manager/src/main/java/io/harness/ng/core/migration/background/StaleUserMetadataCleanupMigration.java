/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.user.spring.UserMetadataRepository;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class StaleUserMetadataCleanupMigration implements NGMigration {
  private final UserMetadataRepository userMetadataRepository;
  private final UserClient userClient;

  @Inject
  public StaleUserMetadataCleanupMigration(UserMetadataRepository userMetadataRepository, UserClient userClient) {
    this.userMetadataRepository = userMetadataRepository;
    this.userClient = userClient;
  }

  @Override
  public void migrate() {
    try {
      int pageIndex = 0;
      int pageSize = 10;

      do {
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        } catch (Exception ex) {
          log.error("[StaleUserMetadataCleanupMigration]: Error while waking up the thread.", ex);
        }

        List<UserMetadata> userMetadataList;
        try {
          userMetadataList = userMetadataRepository.findAll(PageRequest.of(pageIndex++, pageSize)).getContent();
        } catch (Exception ex) {
          log.error(
              "[StaleUserMetadataCleanupMigration]: Exception occurred while fetching UserMetadata batch. Skipping to the next batch of users",
              ex);
          continue;
        }
        if (isEmpty(userMetadataList)) {
          break;
        }

        List<String> userIds =
            userMetadataList.stream().map(userMetadata -> userMetadata.getUserId()).collect(Collectors.toList());

        List<UserInfo> cgUsers;
        try {
          cgUsers =
              CGRestUtils.getResponse(userClient.listUsers(null, UserFilterNG.builder().userIds(userIds).build()));
        } catch (Exception ex) {
          log.error(
              "[StaleUserMetadataCleanupMigration]: Exception occurred while fetching batch of users from current gen. UserIds in current batch: {}. Skipping to next batch of users",
              userIds, ex);
          continue;
        }

        Set<String> cgUserIds =
            new HashSet<>(cgUsers.stream().map(userInfo -> userInfo.getUuid()).collect(Collectors.toSet()));

        Set<String> userIdsToDeleteFromNG = Sets.difference(new HashSet<>(userIds), cgUserIds);
        if (!isEmpty(userIdsToDeleteFromNG)) {
          try {
            userMetadataRepository.deleteAllById(userIdsToDeleteFromNG);
            log.info(
                "[StaleUserMetadataCleanupMigration]: Successfully deleted UserMetadata for the following userIds: {}",
                userIdsToDeleteFromNG);
          } catch (Exception ex) {
            log.error(
                "[StaleUserMetadataCleanupMigration]: Exception occurred while deleting records from current batch. UserIds in current batch: {}. Skipping to the next batch of users",
                userIds, ex);
          }
        }
      } while (true);
    } catch (Exception ex) {
      log.error("[StaleUserMetadataCleanupMigration]: Exception occurred while running the migration.", ex);
    }
  }
}
