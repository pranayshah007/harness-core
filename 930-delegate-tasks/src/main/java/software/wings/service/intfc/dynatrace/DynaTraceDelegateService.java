/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.dynatrace;

import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.DynaTraceConfig;
import software.wings.beans.TaskType;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.dynatrace.DynaTraceApplication;
import software.wings.service.impl.dynatrace.DynaTraceMetricDataRequest;
import software.wings.service.impl.dynatrace.DynaTraceMetricDataResponse;
import software.wings.service.impl.dynatrace.DynaTraceSetupTestNodeData;

import java.util.List;
import javax.validation.constraints.NotNull;

/**
 * Created by rsingh on 01/29/18.
 */
public interface DynaTraceDelegateService {
  @DelegateTaskType(TaskType.DYNA_TRACE_VALIDATE_CONFIGURATION_TASK)
  boolean validateConfig(@NotNull DynaTraceConfig dynaTraceConfig, List<EncryptedDataDetail> encryptedDataDetails);

  DynaTraceMetricDataResponse fetchMetricData(@NotNull DynaTraceConfig dynaTraceConfig,
      @NotNull DynaTraceMetricDataRequest dataRequest, @NotNull List<EncryptedDataDetail> encryptedDataDetails,
      ThirdPartyApiCallLog apiCallLog);

  @DelegateTaskType(TaskType.DYNA_TRACE_GET_TXNS_WITH_DATA_FOR_NODE)
  List<DynaTraceMetricDataResponse> getMetricsWithDataForNode(DynaTraceConfig value,
      List<EncryptedDataDetail> encryptionDetails, DynaTraceSetupTestNodeData setupTestNodeData,
      ThirdPartyApiCallLog thirdPartyApiCallLog);

  @DelegateTaskType(TaskType.DYNA_TRACE_GET_SERVICES)
  List<DynaTraceApplication> getServices(DynaTraceConfig value, List<EncryptedDataDetail> encryptionDetails,
      ThirdPartyApiCallLog thirdPartyApiCallLog, Boolean shouldResolveAllServices);
}
