/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.analysis;

import software.wings.delegatetasks.cv.DelegateCVActivityLogService.Logger;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;

import com.google.common.collect.TreeBasedTable;

/**
 * Created by rsingh on 3/16/18.
 */
public interface MetricCollectionResponse {
  TreeBasedTable<String, Long, NewRelicMetricDataRecord> getMetricRecords(String transactionName, String metricName,
      String appId, String workflowId, String workflowExecutionId, String stateExecutionId, String serviceId,
      String host, String groupName, long collectionStartTime, String cvConfigId, boolean is247Task, String url,
      Logger activityLogger);
}
