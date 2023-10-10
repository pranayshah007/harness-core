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
import io.harness.ngmigration.beans.summary.SimilarWorkflowResult;
import io.harness.ngmigration.dto.SimilarWorkflowDetail;
import io.harness.ngmigration.service.MigrationResourceService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class AsyncSimilarWorkflowHandler extends AsyncTaskHandler {
  @Inject MigrationResourceService migrationResourceService;
  @Inject private HPersistence hPersistence;

  private static final String TASK_TYPE = "SIMILAR_WORKFLOWS";

  @Override
  String getTaskType() {
    return TASK_TYPE;
  }

  @Override
  MigrationTrackRespPayload processTask(
      String apiKey, String accountId, String requestId, MigrationTrackReqPayload reqPayload) {
    List<Set<SimilarWorkflowDetail>> similarWorkflows = migrationResourceService.listSimilarWorkflow(accountId);
    return SimilarWorkflowResult.builder().similarWorkflows(similarWorkflows).build();
  }

  @Override
  HPersistence getHPersistence() {
    return hPersistence;
  }
}
