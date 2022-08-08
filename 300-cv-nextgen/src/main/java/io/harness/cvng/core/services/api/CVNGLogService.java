/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.CVNGLogType;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.logsFilterParams.DeploymentLogsFilter;
import io.harness.cvng.core.beans.params.logsFilterParams.TimeRangeLogsFilter;
import io.harness.cvng.core.entities.CVNGLog;
import io.harness.ng.beans.PageResponse;

import java.util.List;

public interface CVNGLogService {
  void save(List<CVNGLogDTO> callLogs);
  PageResponse<CVNGLogDTO> getOnboardingLogs(
      String accountId, String traceableId, CVNGLogType cvngLogType, int offset, int pageSize);

  List<CVNGLog> getCompleteCVNGLog(String accountId, String VerificationTaskId, CVNGLogType cvngLogType);
  List<ExecutionLogDTO> getExecutionLogDTOs(String accountId, String verificationTaskId);
  PageResponse<CVNGLogDTO> getCVNGLogs(String accountId, String verificationJobInstanceId,
      DeploymentLogsFilter deploymentLogsFilter, PageParams pageParams);
  PageResponse<CVNGLogDTO> getCVNGLogs(String accountId, List<String> verificationTaskIds,
      TimeRangeLogsFilter timeRangeLogsFilter, PageParams pageParams);
}
