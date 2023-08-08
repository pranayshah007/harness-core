/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.service.intf;

import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingHistory;

import java.time.Instant;
import java.util.List;

public interface BusinessMappingHistoryService {
  BusinessMappingHistory save(BusinessMappingHistory businessMappingHistory);
  List<BusinessMappingHistory> getAll(String accountId, String businessMappingId);
  BusinessMappingHistory getLatest(String accountId, String businessMappingId, Instant currentInstant);
  List<BusinessMappingHistory> getInRange(String accountId, Instant startTime, Instant endTime);
  List<BusinessMappingHistory> getInRange(
      String accountId, String businessMappingId, Instant startTime, Instant endTime);
  BusinessMappingHistory update(BusinessMappingHistory businessMappingHistory);
  boolean delete(BusinessMappingHistory businessMappingHistory);
  void handleCreateEvent(BusinessMapping businessMapping, Instant eventTime);
  void handleUpdateEvent(BusinessMapping businessMapping, Instant eventTime);
  void handleDeleteEvent(BusinessMapping businessMapping, Instant eventTime);
}
