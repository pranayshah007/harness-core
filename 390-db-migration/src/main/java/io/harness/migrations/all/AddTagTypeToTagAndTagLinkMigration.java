/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.HarnessTag;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.HarnessTagType;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Add TagType To Harness Tag and TagLink classes.
 * @author rktummala on 04/28/20
 */
@Slf4j
public class AddTagTypeToTagAndTagLinkMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<Account> accounts = new HIterator<>(
             wingsPersistence.createAnalyticsQuery(Account.class).project(Account.ID_KEY2, true).fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();
        try (HIterator<HarnessTag> tags = new HIterator<>(
                 wingsPersistence.createQuery(HarnessTag.class).filter("accountId", account.getUuid()).fetch())) {
          while (tags.hasNext()) {
            HarnessTag tag = tags.next();
            wingsPersistence.updateField(HarnessTag.class, tag.getUuid(), "tagType", HarnessTagType.USER);
          }
        }

        try (HIterator<HarnessTagLink> tagLinks = new HIterator<>(
                 wingsPersistence.createQuery(HarnessTagLink.class).filter("accountId", account.getUuid()).fetch())) {
          while (tagLinks.hasNext()) {
            HarnessTagLink tagLink = tagLinks.next();
            wingsPersistence.updateField(HarnessTagLink.class, tagLink.getUuid(), "tagType", HarnessTagType.USER);
          }
        }
      }
    }
  }
}
