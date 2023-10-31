/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.executionInfra;

import java.util.Map;

public interface ExecutionInfrastructureService {
  String createExecutionInfra(String taskId, Map<String, String> stepTaskIds, String runnerType);
  boolean updateDelegateInfo(String infraRefId, String delegateId, String delegateName);
  ExecutionInfraLocation getExecutionInfra(String id);
  boolean deleteExecutionInfrastructure(String executionInfraUuid);
}
