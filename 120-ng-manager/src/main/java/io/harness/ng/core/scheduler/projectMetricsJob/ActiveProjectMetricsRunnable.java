/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler.projectMetricsJob;

import io.harness.account.AccountClient;
import io.harness.audit.beans.custom.ActiveProjectMetricsDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.beans.PageResponse;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.services.ProjectService;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.user.custom.UserMetadataRepositoryCustom;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ActiveProjectMetricsRunnable implements Runnable {
  @Inject private ProjectService projectService;
  @Inject private UserMetadataRepositoryCustom ngUserService;
  @Inject AccountClient accountClient;
  @Inject AuditClientService auditClientService;
  @Override
  public void run() {
    int pageIndex = 0;
    int pageSize = 100;
    long endTime = System.currentTimeMillis();
    long startTime = endTime - (24 * 60 * 60 * 1000);
    log.info("Starting ActiveProjectMetricsRunnable Job");
    try (ResponseTimeRecorder ignore1 = new ResponseTimeRecorder("Send project activity metrics for accounts")) {
      try {
        do {
          List<String> accountIds = new ArrayList<>();
          PageResponse<AccountDTO> pageResponse =
              CGRestUtils.getResponse(accountClient.listAccounts(pageIndex, pageSize));
          if (pageResponse.size() == 0) {
            break;
          }
          accountIds.addAll(
              pageResponse.getResponse().stream().map(AccountDTO::getIdentifier).collect(Collectors.toList()));
          pageIndex++;
          Map<String, Integer> projectCounts = projectService.getProjectsCountPerAccount(accountIds);
          ActiveProjectMetricsDTO activeProjectMetricsDTO = ActiveProjectMetricsDTO.builder()
                                                                .projectCounts(projectCounts)
                                                                .accountIds(accountIds)
                                                                .startTime(startTime)
                                                                .endTime(endTime)
                                                                .build();
          auditClientService.publishMetrics(activeProjectMetricsDTO);
          log.info("Published metrics for Accounts pageIndex: {}", pageIndex);

        } while (true);
      } catch (Exception ex) {
        log.error("Error occurred while sending metrics for active projects to segment: ", ex);
      }
    }
  }
}
