/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.template.TemplateGallery.GALLERY_KEY;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.template.TemplateGallery;
import software.wings.beans.template.TemplateGallery.TemplateGalleryKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.template.TemplateGalleryService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportedTemplateGalleryMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject TemplateGalleryService templateGalleryService;
  private static final String DEBUG_LINE = "GLOBAL_COMMAND_GALLERY: ";

  @Override
  public void migrate() {
    log.info(DEBUG_LINE + "Starting migration for adding field in template gallery");

    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createAuthorizedQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        UpdateOperations<TemplateGallery> updateOperations =
            wingsPersistence.createUpdateOperations(TemplateGallery.class)
                .set(GALLERY_KEY, templateGalleryService.getAccountGalleryKey());

        Query<TemplateGallery> query = wingsPersistence.createQuery(TemplateGallery.class)
                                           .filter(TemplateGalleryKeys.accountId, account.getUuid())
                                           .field(GALLERY_KEY)
                                           .doesNotExist();
        UpdateResults result = wingsPersistence.update(query, updateOperations);
        log.info("Updated account gallery to have gallery type for account %s ");
        log.info(DEBUG_LINE + "Gallery type update for already existing gallery for account {} resulted in {}",
            account.getUuid(), result);
      }
    }
  }
}
