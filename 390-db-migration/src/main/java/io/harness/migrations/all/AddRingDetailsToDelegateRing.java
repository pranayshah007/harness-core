/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.delegate.utils.DelegateRingConstants;
import io.harness.migrations.Migration;
import io.harness.migrations.SeedDataMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AddRingDetailsToDelegateRing implements Migration, SeedDataMigration {
  @Inject private HPersistence persistence;
  private static final String DELEGATE_IMAGE_TAG = "harness/delegate:latest";
  private static final String UPGRADER_IMAGE_TAG = "harness/upgrader:latest";

  @Override
  public void migrate() {
    log.info("Starting the migration for adding ring details in delegateRing collection.");

    for (String ringName : Arrays.asList(DelegateRingConstants.RING_NAME_1, DelegateRingConstants.RING_NAME_2,
             DelegateRingConstants.RING_NAME_3, DelegateRingConstants.RING_NAME_4)) {
      checkAndInsertDelegateRing(ringName);
    }

    log.info("Migration complete for adding ring details in delegateRing collection.");
  }

  private void checkAndInsertDelegateRing(String ringName) {
    try {
      DelegateRing delegateRing =
          persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName).get();
      if (delegateRing != null) {
        log.info("Delegate Ring {} exists. Hence skipping migration for delegate ring {}.", ringName, ringName);
      } else {
        persistence.save(delegateRing(ringName));
        log.info("Added {} details.", ringName);
      }
    } catch (Exception e) {
      log.error("Exception occurred during migration of adding delegate {}.", ringName, e);
    }
  }

  private DelegateRing delegateRing(String ringName) {
    return new DelegateRing(ringName, DELEGATE_IMAGE_TAG, UPGRADER_IMAGE_TAG);
  }
}
