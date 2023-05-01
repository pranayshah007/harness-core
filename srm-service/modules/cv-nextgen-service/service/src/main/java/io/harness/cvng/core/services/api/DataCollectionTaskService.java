/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DataCollectionTaskService {
  void save(DataCollectionTask dataCollectionTask);

  Optional<DataCollectionTask> getNextTask(String accountId, String dataCollectionWorkerId);

  Optional<DataCollectionTaskDTO> getNextTaskDTO(String accountId, String dataCollectionWorkerId);

  List<DataCollectionTaskDTO> getNextTaskDTOs(String accountId, String dataCollectionWorkerId);

  DataCollectionTask getDataCollectionTask(String dataCollectionTaskId);

  DataCollectionTask getFirstDataCollectionTaskWithStatusAfterStartTime(
      String verificationTaskId, DataCollectionExecutionStatus status, Instant startTime);

  void updateTaskStatus(DataCollectionTaskResult dataCollectionTaskResult);

  DataCollectionTask updateRetry(ProjectParams projectParams, String identifier);

  List<String> createSeqTasks(List<DataCollectionTask> dataCollectionTasks);

  void abortDeploymentDataCollectionTasks(List<String> verificationTaskIds);

  DataCollectionTask getLastDataCollectionTask(String accountId, String verificationTaskId);

  List<DataCollectionTask> getLatestDataCollectionTasks(String accountId, String verificationTaskId, int count);

  List<DataCollectionTask> getAllDataCollectionTasks(String accountId, String verificationTaskId);

  List<DataCollectionTask> getAllNonFinalDataCollectionTasks(String accountId, String verificationTaskId);

  void populateMetricPack(CVConfig cvConfig);

  void validateIfAlreadyExists(DataCollectionTask dataCollectionTask);

  void updatePerpetualTaskStatus(DataCollectionTask dataCollectionTask);
}
