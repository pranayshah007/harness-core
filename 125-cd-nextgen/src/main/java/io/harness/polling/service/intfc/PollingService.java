/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.intfc;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.dto.PollingResponseDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.polling.bean.PolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.contracts.PollingItem;

import java.util.List;
import javax.validation.Valid;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(HarnessTeam.CDC)
public interface PollingService {
  PollingResponseDTO save(@Valid PollingDocument pollingDocument);

  PollingDocument get(String accountId, String pollingDocId);

  List<PollingDocument> getMany(String accountId, List<String> pollingDocId);

  List<String> getUuidsBySignatures(String accountId, List<String> signatures);

  List<PollingDocument> getByConnectorRef(String accountId, String connectorRef);

  void delete(PollingDocument pollingDocument);

  boolean attachPerpetualTask(String accountId, String pollDocId, String perpetualTaskId);

  void updateFailedAttempts(String accountId, String pollingDocId, int failedAttempts);

  void updatePolledResponse(String accountId, String pollingDocId, PolledResponse polledResponse);

  PollingResponseDTO subscribe(PollingItem pollingItem) throws InvalidRequestException;

  boolean unsubscribe(PollingItem pollingItem);

  void deleteAtAllScopes(Scope scope);

  void resetPerpetualTasksForConnector(String accountId, String connectorRef);

  PollingInfoForTriggers getPollingInfoForTriggers(String accountId, String pollingDocId);

  boolean updateTriggerPollingStatus(String accountId, List<String> signatures, boolean status, String errorMessage,
      List<String> lastCollectedVersions, Long validityIntervalForErrorStatusMillis);
}
