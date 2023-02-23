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

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.dl.WingsPersistence;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

/**
 * @author marklu on 9/4/19
 */
@Slf4j
public class SettingAttributesCategoryMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    Query<SettingAttribute> settingAttributeWithoutCategoryQuery =
        wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority)
            .field(SettingAttributeKeys.category)
            .doesNotExist();

    int count = 0;
    int updateCount = 0;
    try (
        HIterator<SettingAttribute> settingAttributes = new HIterator<>(settingAttributeWithoutCategoryQuery.fetch())) {
      while (settingAttributes.hasNext()) {
        SettingAttribute settingAttribute = settingAttributes.next();
        SettingCategory category =
            SettingCategory.getCategory(SettingVariableTypes.valueOf(settingAttribute.getValue().getType()));
        if (category != null) {
          UpdateOperations<SettingAttribute> updateOperations =
              wingsPersistence.createUpdateOperations(SettingAttribute.class)
                  .set(SettingAttributeKeys.category, category.name());
          wingsPersistence.update(settingAttribute, updateOperations);

          log.info("Setting attribute '{}' with id {} has been updated to category {}", settingAttribute.getName(),
              settingAttribute.getUuid(), category);
          updateCount++;
        }
        count++;
      }
    }

    log.info("Found {} setting attributes with category from database. Updated {} of them with proper categories",
        count, updateCount);
  }
}
