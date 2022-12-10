/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.yaml.gitSync.beans.YamlGitConfig.ENTITY_ID_KEY;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.ENTITY_TYPE_KEY;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.SYNC_MODE_KEY;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.EntityType;
import software.wings.dl.WingsPersistence;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YamlGitConfigMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Running YamlGitConfigMigration");

    try (HIterator<YamlGitConfig> yamlGitConfigHIterator =
             new HIterator<>(wingsPersistence.createQuery(YamlGitConfig.class).fetch())) {
      for (YamlGitConfig yamlGitConfig : yamlGitConfigHIterator) {
        updateYamlGitConfig(yamlGitConfig);
      }
    }

    log.info("Completed running YamlGitConfigMigration");
  }

  private void updateYamlGitConfig(YamlGitConfig yamlGitConfig) {
    if (isEmpty(yamlGitConfig.getEntityId()) && yamlGitConfig.getEntityType() == null) {
      Map<String, Object> keyValuePairs = new HashMap<>();
      keyValuePairs.put(SYNC_MODE_KEY, SyncMode.BOTH);
      keyValuePairs.put(ENTITY_ID_KEY, yamlGitConfig.getAccountId());
      keyValuePairs.put(ENTITY_TYPE_KEY, EntityType.ACCOUNT);

      wingsPersistence.updateFields(YamlGitConfig.class, yamlGitConfig.getUuid(), keyValuePairs);
    }
  }
}
