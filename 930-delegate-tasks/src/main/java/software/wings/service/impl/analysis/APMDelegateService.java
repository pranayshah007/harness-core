/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.ThirdPartyApiCallLog;

public interface APMDelegateService {
  @DelegateTaskType(TaskType.APM_VALIDATE_CONNECTOR_TASK) boolean validateCollector(APMValidateCollectorConfig config);
  @DelegateTaskType(TaskType.APM_GET_TASK)
  String fetch(APMValidateCollectorConfig config, ThirdPartyApiCallLog apiCallLog);
}
