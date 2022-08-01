/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateTaskResponse;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import org.mongodb.morphia.query.Query;

public interface DelegateTaskService {
  void touchExecutingTasks(String accountId, String delegateId, List<String> delegateTaskIds);

  void processDelegateResponse(
      String accountId, String delegateId, String taskId, @Valid DelegateTaskResponse response);

  void handleResponse(DelegateTask delegateTask, Query<DelegateTask> taskQuery, DelegateTaskResponse response);

  void publishTaskProgressResponse(
      String accountId, String driverId, String delegateTaskId, DelegateProgressData responseData);

  Optional<DelegateTask> fetchDelegateTask(String accountId, String taskId);
}
