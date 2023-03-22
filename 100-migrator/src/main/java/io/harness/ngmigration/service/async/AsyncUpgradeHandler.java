/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.async;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigrationTrackReqPayload;
import io.harness.beans.MigrationTrackRespPayload;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.service.MigrationResourceService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class AsyncUpgradeHandler extends AsyncTaskHandler {
  @Inject MigrationResourceService migrationResourceService;
  @Inject private HPersistence hPersistence;

  private static final String TASK_TYPE = "MIGRATION";

  @Override
  String getTaskType() {
    return TASK_TYPE;
  }

  @Override
  MigrationTrackRespPayload processTask(
      String apiKey, String accountId, String requestId, MigrationTrackReqPayload reqPayload) {
    ImportDTO importDTO = (ImportDTO) reqPayload;
    importDTO.setAccountIdentifier(accountId);
    return migrationResourceService.save(apiKey, importDTO);
  }

  @Override
  HPersistence getHPersistence() {
    return hPersistence;
  }
}
