/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.events.timeseries.service.intfc;

import io.harness.batch.processing.events.timeseries.data.CostEventData;

import java.util.List;

public interface CostEventService {
  boolean create(List<CostEventData> costEventDataList);

  boolean updateDeploymentEvent(CostEventData costEventData);

  List<CostEventData> getEventsForWorkload(
      String accountId, String clusterId, String instanceId, String costEventType, long startTimeMillis);
}
