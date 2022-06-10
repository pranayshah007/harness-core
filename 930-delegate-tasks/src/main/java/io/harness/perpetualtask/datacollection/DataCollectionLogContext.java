/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionType;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

public class DataCollectionLogContext extends AutoLogContext {
  public static final String DATA_COLLECTION_WORKER_ID = "dataCollectionWorkerId";
  public static final String VERIFICATION_TYPE = "verificationType";
  public static final String ACCOUNT_ID = "accountId";
  public static final String VERIFICATION_TASK_ID = "verificationTaskId";
  public static final String TRACING_ID = "tracingId";

  private static Map<String, String> getContext(String dataCollectionWorkerId, DataCollectionType dataCollectionType) {
    Map<String, String> contextMap = new HashMap<>();
    if (isNotEmpty(dataCollectionWorkerId)) {
      contextMap.put(DATA_COLLECTION_WORKER_ID, dataCollectionWorkerId);
    }
    if (dataCollectionType != null) {
      contextMap.put(VERIFICATION_TYPE, dataCollectionType.name());
    }
    return contextMap;
  }

  private static Map<String, String> getContext(DataCollectionTaskDTO dataCollectionTaskDTO) {
    Map<String, String> contextMap = new HashMap<>();
    if (isNotEmpty(dataCollectionTaskDTO.getAccountId())) {
      contextMap.put(ACCOUNT_ID, dataCollectionTaskDTO.getAccountId());
    }
    if (isNotEmpty(dataCollectionTaskDTO.getVerificationTaskId())) {
      contextMap.put(VERIFICATION_TASK_ID, dataCollectionTaskDTO.getVerificationTaskId());
    }
    return contextMap;
  }

  private static Map<String, String> getContext(String accountId, DataCollectionRequest dataCollectionRequest) {
    Map<String, String> contextMap = new HashMap<>();
    if (isNotEmpty(accountId)) {
      contextMap.put(ACCOUNT_ID, accountId);
    }
    if (isNotEmpty(dataCollectionRequest.getTracingId())) {
      contextMap.put(TRACING_ID, dataCollectionRequest.getTracingId());
    }
    return contextMap;
  }

  public DataCollectionLogContext(
      String dataCollectionWorkerId, DataCollectionType dataCollectionType, OverrideBehavior behavior) {
    super(getContext(dataCollectionWorkerId, dataCollectionType), behavior);
  }

  public DataCollectionLogContext(DataCollectionTaskDTO dataCollectionTaskDTO) {
    super(getContext(dataCollectionTaskDTO), OVERRIDE_ERROR);
  }

  public DataCollectionLogContext(String accountId, DataCollectionRequest dataCollectionRequest) {
    super(getContext(accountId, dataCollectionRequest), OVERRIDE_ERROR);
  }
}
