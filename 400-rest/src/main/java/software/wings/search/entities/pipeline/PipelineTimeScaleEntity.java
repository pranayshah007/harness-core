/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.pipeline;

import io.harness.event.reconciliation.service.LookerEntityReconService;
import io.harness.event.reconciliation.service.PipelineEntityReconServiceImpl;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.Pipeline;
import software.wings.search.framework.ChangeHandler;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.timescale.migrations.MigratePipelinesToTimeScaleDB;

import com.google.inject.Inject;
import java.util.Set;

public class PipelineTimeScaleEntity implements TimeScaleEntity<Pipeline> {
  @Inject private PipelineTimescaleChangeDataHandler pipelineTimescaleChangeDataHandler;
  @Inject private PipelineEntityReconServiceImpl pipelineEntityReconService;
  @Inject private MigratePipelinesToTimeScaleDB migratePipelinesToTimeScaleDB;

  public static final Class<Pipeline> SOURCE_ENTITY_CLASS = Pipeline.class;

  @Override
  public Class<Pipeline> getSourceEntityClass() {
    return SOURCE_ENTITY_CLASS;
  }

  @Override
  public ChangeHandler getChangeHandler() {
    return pipelineTimescaleChangeDataHandler;
  }

  @Override
  public LookerEntityReconService getReconService() {
    return pipelineEntityReconService;
  }

  @Override
  public boolean toProcessChangeEvent(Set<String> accountIds, PersistentEntity entity) {
    Pipeline pipeline = (Pipeline) entity;

    return accountIds.contains(pipeline.getAccountId());
  }

  @Override
  public boolean runMigration(String accountId) {
    return migratePipelinesToTimeScaleDB.runTimeScaleMigration(accountId);
  }

  @Override
  public void savetoTimescale(Pipeline entity) {}

  @Override
  public void deleteFromTimescale(String id) {}
}
