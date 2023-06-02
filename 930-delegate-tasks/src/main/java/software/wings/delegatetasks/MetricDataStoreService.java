/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import software.wings.beans.dto.NewRelicMetricDataRecord;

import java.util.List;

/**
 * Created by rsingh on 5/18/17.
 */
public interface MetricDataStoreService {
  boolean saveNewRelicMetrics(String accountId, String applicationId, String stateExecutionId, String delegateTaskID,
      List<NewRelicMetricDataRecord> metricData) throws Exception;
}
