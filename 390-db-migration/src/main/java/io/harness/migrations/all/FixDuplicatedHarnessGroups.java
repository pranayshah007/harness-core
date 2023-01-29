/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.security.HarnessUserGroup;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Create single Harness user group with all members, delete existing groups
 */

@Slf4j
public class FixDuplicatedHarnessGroups implements Migration {
  @Inject private WingsPersistence persistence;

  @Override
  public void migrate() {
    try {
      log.error("Fixing duplicate Harness user groups issue.");

      Set<String> memberIds = new HashSet<>();

      try (HIterator<HarnessUserGroup> harnessUserGroupIterator =
               new HIterator<>(persistence.createQuery(HarnessUserGroup.class, excludeAuthority).fetch())) {
        while (harnessUserGroupIterator.hasNext()) {
          final HarnessUserGroup group = harnessUserGroupIterator.next();
          memberIds.addAll(group.getMemberIds());
        }
      }

      log.info("Harness support users: " + memberIds.toString());

      Query<HarnessUserGroup> query = persistence.createQuery(HarnessUserGroup.class, excludeAuthority);
      persistence.delete(query);

      persistence.save(HarnessUserGroup.builder().memberIds(memberIds).name("readOnly").build());

    } catch (Exception e) {
      log.error("Error while fixing duplicated Harness user groups", e);
    }
  }
}
