/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.gitsync;

import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.OnPrimaryManagerMigration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.yaml.gitSync.GitChangeSetRunnableHelper;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.YamlChangeSet.YamlChangeSetKeys;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class SetQueueKeyYamChangeSetMigration implements OnPrimaryManagerMigration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private YamlChangeSetService yamlChangeSetService;
  @Inject private GitChangeSetRunnableHelper gitChangeSetRunnableHelper;

  @Override
  public void migrate() {
    log.info("Running migration SetQueueKeyYamChangeSetMigration");

    gitChangeSetRunnableHelper.handleOldQueuedChangeSets(wingsPersistence);

    populateQueueKeyAndMetadata();

    log.info("Completed migration SetQueueKeyYamChangeSetMigration");
  }

  private void populateQueueKeyAndMetadata() {
    Query<YamlChangeSet> query = wingsPersistence.createQuery(YamlChangeSet.class, excludeAuthority)
                                     .field(YamlChangeSetKeys.status)
                                     .in(ImmutableList.of(Status.QUEUED, Status.RUNNING));

    try (HIterator<YamlChangeSet> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        YamlChangeSet yamlChangeSet = records.next();
        try {
          // Is the record already being processed ? If yes then skip
          if (StringUtils.isBlank(yamlChangeSet.getQueueKey()) || yamlChangeSet.getGitSyncMetadata() == null) {
            log.info("Updating yamlchangeset id = [{}]", yamlChangeSet.getUuid());
            yamlChangeSetService.populateGitSyncMetadata(yamlChangeSet);
            updateRequiredFieldsOnly(yamlChangeSet);
            log.info("Successfully updated yamlchangeset id = [{}]", yamlChangeSet.getUuid());
          }
        } catch (Exception e) {
          log.error("Error while updating yamlchangeset id =" + yamlChangeSet.getUuid(), e);
        }
      }
    }
  }

  void updateRequiredFieldsOnly(YamlChangeSet yamlChangeSetWithMetadata) {
    final UpdateOperations<YamlChangeSet> updateOperations =
        wingsPersistence.createUpdateOperations(YamlChangeSet.class);
    setUnset(updateOperations, YamlChangeSetKeys.gitSyncMetadata, yamlChangeSetWithMetadata.getGitSyncMetadata());
    setUnset(updateOperations, YamlChangeSetKeys.queueKey, yamlChangeSetWithMetadata.getQueueKey());
    wingsPersistence.update(yamlChangeSetWithMetadata, updateOperations);
  }
}
