/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.tags;

import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.event.reconciliation.service.TagsEntityReconServiceImpl;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.HarnessTagLink;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigrateTagLinksToTImeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class TagLinksTimeScaleEntity implements TimeScaleEntity<HarnessTagLink> {
  @Inject private TagLinkTimescaleChangeHandler tagLinkTimescaleChangeHandler;
  @Inject private TagsEntityReconServiceImpl tagsEntityReconService;
  @Inject private MigrateTagLinksToTImeScaleDB migrateTagLinksToTImeScaleDB;

  public static final Class<HarnessTagLink> SOURCE_ENTITY_CLASS = HarnessTagLink.class;

  @Override
  public Class<HarnessTagLink> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return tagLinkTimescaleChangeHandler;
  }

  @Override
  public LookerEntityReconService getReconService() {
    return tagsEntityReconService;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    HarnessTagLink harnessTagLink = (HarnessTagLink) entity;

    return accountIds.contains(harnessTagLink.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migrateTagLinksToTImeScaleDB.runTimeScaleMigration(accountId);
  }

  @Override
  public void savetoTimescale(HarnessTagLink entity) {}

  @Override
  public void deleteFromTimescale(String id) {}
}
