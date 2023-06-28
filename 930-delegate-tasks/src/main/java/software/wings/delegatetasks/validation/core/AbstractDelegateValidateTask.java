/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.core;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.network.Http.connectableHttpUrl;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.task.validation.DelegateConnectionResultDetail;

import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by brett on 11/1/17
 */
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class AbstractDelegateValidateTask implements DelegateValidateTask {
  protected String delegateTaskId;

  private String accountId;
  private String delegateId;
  private String taskType;
  private Consumer<List<DelegateConnectionResultDetail>> consumer;
  private TaskData taskData;
  private List<ExecutionCapability> executionCapabilities;

  public AbstractDelegateValidateTask(String delegateId, DelegateTaskPackage delegateTaskPackage,
      Consumer<List<DelegateConnectionResultDetail>> consumer) {
    this.accountId = delegateTaskPackage.getAccountId();
    this.delegateId = delegateId;
    this.delegateTaskId = delegateTaskPackage.getDelegateTaskId();
    this.taskType = delegateTaskPackage.getData().getTaskType();
    this.consumer = consumer;
    this.taskData = delegateTaskPackage.getData();
    this.executionCapabilities = delegateTaskPackage.getExecutionCapabilities();
  }

  @Override
  public List<DelegateConnectionResultDetail> validationResults() {
    try (TaskLogContext ignore = new TaskLogContext(this.delegateTaskId, OVERRIDE_ERROR)) {
      List<DelegateConnectionResultDetail> results = null;
      try {
        long startTime = System.currentTimeMillis();
        results = validate();
        long duration = System.currentTimeMillis() - startTime;
        for (DelegateConnectionResultDetail result : results) {
          result.setAccountId(accountId);
          result.setDelegateId(delegateId);
          if (result.getDuration() == 0) {
            result.setDuration(duration);
          }
        }
      } catch (Exception exception) {
        log.error("Unexpected error validating delegate task.", exception);
      } finally {
        if (consumer != null) {
          consumer.accept(results);
        }
      }
      return results;
    } catch (Exception e) {
      log.error("Unexpected error executing delegate task {}", delegateId, e);
    }
    return emptyList();
  }

  public List<DelegateConnectionResultDetail> validate() {
    try {
      String criteria = getCriteria().get(0);
      return singletonList(DelegateConnectionResultDetail.builder()
                               .criteria(criteria)
                               .validated(connectableHttpUrl(criteria, false))
                               .build());
    } catch (Exception e) {
      return emptyList();
    }
  }

  public Object[] getParameters() {
    return getTaskData().getParameters();
  }

  protected String getTaskType() {
    return taskType;
  }

  protected TaskData getTaskData() {
    return taskData;
  }

  protected List<ExecutionCapability> getExecutionCapabilities() {
    return executionCapabilities;
  }
}
