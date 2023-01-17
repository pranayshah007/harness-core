/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.services;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.LogElement;

import java.io.IOException;
import java.util.List;

/**
 * Created by rsingh on 5/18/17.
 */
public interface LogAnalysisStoreService {
  boolean save(DelegateStateType stateType, String accountId, String appId, String cvConfigId, String stateExecutionId,
               String workflowId, String workflowExecutionId, String serviceId, String delegateTaskId,
               List<LogElement> splunkLogs) throws IOException;
}
