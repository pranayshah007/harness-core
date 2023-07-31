/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.pms.execution.ExecutionStatus;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
public interface NGTriggerEventsService {
  Criteria formEventCriteria(String accountId, String eventCorrelationId, List<ExecutionStatus> statusList);
  ResponseDTO<PollingInfoForTriggers> getPollingInfo(String accountId, String pollingDocId);
  Criteria formTriggerEventCriteria(String accountId, String orgId, String projectId, String targetIdentifier,
      String identifier, String searchTerm, List<ExecutionStatus> statusList);

  Page<TriggerEventHistory> getEventHistory(Criteria criteria, Pageable pageable);

  void deleteAllForPipeline(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  void deleteTriggerEventHistory(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String triggerIdentifier);
}
