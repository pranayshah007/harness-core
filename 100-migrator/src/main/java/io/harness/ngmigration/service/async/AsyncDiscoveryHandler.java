/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.async;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigrationTrackReqPayload;
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.DiscoverySummaryReqDTO;
import io.harness.ngmigration.beans.summary.DiscoverySummaryResult;
import io.harness.ngmigration.service.AccountAnalysisService;
import io.harness.persistence.HPersistence;

import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class AsyncDiscoveryHandler extends AsyncTaskHandler {
  @Inject private HPersistence hPersistence;
  @Inject AccountAnalysisService accountSummaryService;

  private static final String TASK_TYPE = "DISCOVERY";

  @Override
  String getTaskType() {
    return TASK_TYPE;
  }

  @Override
  MigrationTrackRespPayload processTask(
      String apiKey, String accountId, String requestId, MigrationTrackReqPayload reqPayload) {
    DiscoverySummaryReqDTO summaryReqDTO = (DiscoverySummaryReqDTO) reqPayload;
    Map<NGMigrationEntityType, BaseSummary> summary =
        accountSummaryService.getSummary(accountId, summaryReqDTO.getAppId());
    return DiscoverySummaryResult.builder().summary(summary).build();
  }

  @Override
  HPersistence getHPersistence() {
    return hPersistence;
  }
}
