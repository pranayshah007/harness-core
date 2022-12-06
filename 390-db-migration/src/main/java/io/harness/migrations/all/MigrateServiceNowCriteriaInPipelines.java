/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.exception.ExceptionUtils;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.PipelineService;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class MigrateServiceNowCriteriaInPipelines implements Migration {
  @Inject private PipelineService pipelineService;
  @Inject private AppService appService;
  @Inject private WingsPersistence wingsPersistence;
  private final String DEBUG_LINE = " SERVICENOW_CRITERIA_MIGRATION: ";

  @Inject private AccountService accountService;

  public void migrate() {
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      String accountId = account.getUuid();
      log.info(StringUtils.join(DEBUG_LINE, "Starting Servicenow Critreia migration for accountId:", accountId));
      migrate(account);
    }
  }

  public void migrate(Account account) {
    log.info(StringUtils.join(
        DEBUG_LINE, "Starting Servicenow Critreia migration for Pipelines, accountId ", account.getUuid()));

    List<Pipeline> pipelines = io.harness.migrations.all.WorkflowAndPipelineMigrationUtils.fetchAllPipelinesForAccount(
        wingsPersistence, pipelineService, account.getUuid());

    for (Pipeline pipeline : pipelines) {
      try {
        migrate(pipelineService.readPipeline(pipeline.getAppId(), pipeline.getUuid(), true));
      } catch (Exception e) {
        log.error("[SERIVCENOW_CRITERIA_MIGRATION] Migration failed for PipelineId: " + pipeline.getUuid()
            + ExceptionUtils.getMessage(e));
      }
    }
  }

  public void migrate(Pipeline pipeline) {
    boolean modified = false;
    // Migrate each stage

    for (PipelineStage stage : pipeline.getPipelineStages()) {
      PipelineStageElement stageElement = stage.getPipelineStageElements().get(0);

      if (stageElement.getType().equals(StateType.APPROVAL.name())) {
        log.info("Migrating approval state in pipeline");
        modified =
            WorkflowAndPipelineMigrationUtils.updateServiceNowProperties(stageElement.getProperties()) || modified;
      }
    }

    if (modified) {
      try {
        pipelineService.update(pipeline, true, false);
        log.info("--- Pipeline updated: {}, {}", pipeline.getUuid(), pipeline.getName());
        Thread.sleep(100);
      } catch (Exception e) {
        log.error("[SERVICENOW_CRITERIA_ERROR] Error updating pipeline " + pipeline.getUuid(), e);
      }
    }
  }
}
