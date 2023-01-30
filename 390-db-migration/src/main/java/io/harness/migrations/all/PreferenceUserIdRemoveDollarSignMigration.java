/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Preference;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreferenceUserIdRemoveDollarSignMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    Query<Preference> query = wingsPersistence.createQuery(Preference.class);

    try (HIterator<Preference> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        Preference preference = records.next();
        String userId = preference.getUserId();
        if (!isEmpty(userId) && userId.endsWith("$")) {
          String newUserId = userId.substring(0, userId.length() - 1);
          preference.setUserId(newUserId);
          wingsPersistence.save(preference);
          log.info("Updated user Id {} for preference {}", preference.getUserId(), preference.getUuid());
        }
      }
    }
  }
}
