/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.OutboxEvent.OutboxEventKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DeleteFailedNgDelegateTokenMigration implements Migration {
  @Inject private HPersistence persistence;

  // we are deleting only those audit events that are logged before 1 March
  private static final String MARCH_1ST_2022_IN_MS = "1646092800000";

  private static final List<String> EventTypesToDelete =
      Arrays.asList("DelegateNgTokenCreateEvent", "DelegateNgTokenRevokeEvent");

  @Override
  public void migrate() {
    log.info("Starting the migration for deleting failed ng delegate token audit events.");
    List<String> idsToDelete = new ArrayList<>();

    int deleted = 0;
    try (HIterator<OutboxEvent> iterator = new HIterator<>(persistence.createQuery(OutboxEvent.class)
                                                               .field(OutboxEventKeys.eventType)
                                                               .in(EventTypesToDelete)
                                                               .field(OutboxEventKeys.createdAt)
                                                               .lessThan(MARCH_1ST_2022_IN_MS)
                                                               .fetch())) {
      while (iterator.hasNext()) {
        idsToDelete.add(iterator.next().getId());

        deleted++;
        if (deleted != 0 && idsToDelete.size() % 500 == 0) {
          persistence.delete(persistence.createQuery(OutboxEvent.class).field(OutboxEventKeys.id).in(idsToDelete));
          log.info("deleted: {} ng delegate token outbox records", deleted);
          idsToDelete.clear();
        }
      }

      if (!idsToDelete.isEmpty()) {
        persistence.delete(persistence.createQuery(OutboxEvent.class).field(OutboxEventKeys.id).in(idsToDelete));
        log.info("deleted: {} ng delegate token outbox records", deleted);
      }
    } catch (Exception e) {
      log.error("Error occurred during migration for deleting all failed ng delegate token audit events.", e);
    }
    log.info(
        "Migration complete for deleting all failed ng delegate token audit events. Deleted " + deleted + " records.");
  }
}