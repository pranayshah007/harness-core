/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.SettingCategory.SETTING;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.query.Query;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoveUsageRestrictionForApplicationDefaultsMigration implements Migration {
  private static final String DEBUG_LINE = "USAGE_RESTRICTION_APPLICATION_DEFAULTS";

  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("{}: Starting", DEBUG_LINE);

    List<Account> accountList = wingsPersistence.createQuery(Account.class).asList();

    for (Account account : accountList) {
      final DBCollection collection = wingsPersistence.getCollection(SettingAttribute.class);
      BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

      log.info("{}: Starting for accountId: {}", DEBUG_LINE, account.getUuid());

      int i = 1;
      boolean isBulkWriteNeeded = false;
      Query<SettingAttribute> settingAttributeAccountQuery =
          wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority)
              .filter(SettingAttributeKeys.accountId, account.getUuid())
              .filter(SettingAttributeKeys.category, SETTING)
              .filter(SettingAttributeKeys.value_type, SettingVariableTypes.STRING);

      try (HIterator<SettingAttribute> records = new HIterator<>(settingAttributeAccountQuery.fetch())) {
        while (records.hasNext()) {
          SettingAttribute settingAttribute = records.next();

          if (i % 1000 == 0 && isBulkWriteNeeded) {
            bulkWriteOperation.execute();
            bulkWriteOperation = collection.initializeUnorderedBulkOperation();
            isBulkWriteNeeded = false;
            log.info("Entity:{} {} updated", SettingAttribute.class.getSimpleName(), i);
          }
          ++i;

          // only application defaults, exclude account defaults
          if (!GLOBAL_APP_ID.equals(settingAttribute.getAppId()) && settingAttribute.getUsageRestrictions() != null) {
            isBulkWriteNeeded = true;
            bulkWriteOperation
                .find(wingsPersistence.createQuery(SettingAttribute.class)
                          .filter(SettingAttributeKeys.uuid, settingAttribute.getUuid())
                          .getQueryObject())
                .updateOne(new BasicDBObject("$unset", new BasicDBObject(SettingAttributeKeys.usageRestrictions, "")));
          }
        }
      } catch (Exception ex) {
        log.error("{}: Failed to fetch SettingAttributes for accountId: {}", account.getUuid(), DEBUG_LINE, ex);
      }
      if (i % 1000 != 1 && isBulkWriteNeeded) {
        bulkWriteOperation.execute();
      }
      log.info("{}: Done for accountId: {}", DEBUG_LINE, account.getUuid());
    }
    log.info("{}: Done Migration", DEBUG_LINE);
  }
}
