/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.service.intfc;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.exception.InvalidRequestException;
import io.harness.polling.bean.PolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.contracts.PollingItem;

import java.util.List;
import javax.validation.Valid;

@OwnedBy(HarnessTeam.CDC)
public interface PollingService {
  String save(@Valid PollingDocument pollingDocument);

  PollingDocument get(String accountId, String pollingDocId);

  List<PollingDocument> getByConnectorRef(String accountId, String connectorRef);

  void delete(PollingDocument pollingDocument);

  boolean attachPerpetualTask(String accountId, String pollDocId, String perpetualTaskId);

  void updateFailedAttempts(String accountId, String pollingDocId, int failedAttempts);

  void updatePolledResponse(String accountId, String pollingDocId, PolledResponse polledResponse);

  String subscribe(PollingItem pollingItem) throws InvalidRequestException;

  boolean unsubscribe(PollingItem pollingItem);

  void deleteAtAllScopes(Scope scope);

  void resetPerpetualTasksForConnector(String accountId, String connectorRef);

  PollingInfoForTriggers getPollingInfoForTriggers(String accountId, String pollingDocId);
}
