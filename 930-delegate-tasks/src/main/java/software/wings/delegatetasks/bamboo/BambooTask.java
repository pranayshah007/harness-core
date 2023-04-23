/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.BambooConfig;
import software.wings.helpers.ext.bamboo.BambooService;
import software.wings.helpers.ext.bamboo.Result;
import software.wings.sm.states.FilePathAssertionEntry;
import software.wings.sm.states.ParameterEntry;

import com.google.common.collect.Maps;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class BambooTask extends AbstractDelegateRunnableTask {
  @Inject private BambooService bambooService;

  public BambooTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public BambooExecutionResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public BambooExecutionResponse run(Object[] parameters) {
    BambooExecutionResponse bambooExecutionResponse = new BambooExecutionResponse();
    log.info("In Bamboo Task run method");
    try {
      bambooExecutionResponse = run((BambooConfig) parameters[0], (List<EncryptedDataDetail>) parameters[1],
          (String) parameters[2], (List<ParameterEntry>) parameters[3]);
    } catch (Exception e) {
      log.warn("Failed to execute Bamboo verification task: " + ExceptionUtils.getMessage(e), e);
      bambooExecutionResponse.setExecutionStatus(ExecutionStatus.FAILED);
    }
    log.info("Bamboo task  completed");
    return bambooExecutionResponse;
  }

  public BambooExecutionResponse run(BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails,
      String planKey, List<ParameterEntry> parameterEntries) {
    BambooExecutionResponse bambooExecutionResponse = new BambooExecutionResponse();
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
    String errorMessage = null;
    try {
      Map<String, String> evaluatedParameters = Maps.newLinkedHashMap();
      if (isNotEmpty(parameterEntries)) {
        parameterEntries.forEach(
            parameterEntry -> evaluatedParameters.put(parameterEntry.getKey(), parameterEntry.getValue()));
      }
      String buildResultKey = bambooService.triggerPlan(bambooConfig, encryptionDetails, planKey, evaluatedParameters);
      Result result = waitForBuildExecutionToFinish(bambooConfig, encryptionDetails, buildResultKey);
      String buildState = result.getBuildState();
      if (result == null || buildState == null) {
        executionStatus = ExecutionStatus.FAILED;
        log.info("Bamboo execution failed for plan {}", planKey);
      } else {
        if (!"Successful".equalsIgnoreCase(buildState)) {
          executionStatus = ExecutionStatus.FAILED;
          log.info("Build result for Bamboo url {}, plan key {}, build key {} is Failed. Result {}",
              bambooConfig.getBambooUrl(), planKey, buildResultKey, result);
        }
        bambooExecutionResponse.setProjectName(result.getProjectName());
        bambooExecutionResponse.setPlanName(result.getPlanName());
        bambooExecutionResponse.setBuildNumber(result.getBuildNumber());
        bambooExecutionResponse.setBuildStatus(result.getBuildState());
        bambooExecutionResponse.setBuildUrl(result.getBuildUrl());
        bambooExecutionResponse.setParameters(parameterEntries);
      }
    } catch (Exception e) {
      log.warn("Failed to execute Bamboo verification task: " + ExceptionUtils.getMessage(e), e);
      errorMessage = ExceptionUtils.getMessage(e);
      executionStatus = ExecutionStatus.FAILED;
    }
    bambooExecutionResponse.setErrorMessage(errorMessage);
    bambooExecutionResponse.setExecutionStatus(executionStatus);
    return bambooExecutionResponse;
  }

  private Result waitForBuildExecutionToFinish(
      BambooConfig bambooConfig, List<EncryptedDataDetail> encryptionDetails, String buildResultKey) {
    Result result;
    do {
      log.info("Waiting for build execution {} to finish", buildResultKey);
      sleep(ofSeconds(5));
      result = bambooService.getBuildResult(bambooConfig, encryptionDetails, buildResultKey);
      log.info("Build result for build key {} is {}", buildResultKey, result);
    } while (result.getBuildState() == null || result.getBuildState().equalsIgnoreCase("Unknown"));

    // Get the build result
    log.info("Build execution for build key {} is finished. Result:{} ", buildResultKey, result);
    return result;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  public static final class BambooExecutionResponse implements DelegateTaskNotifyResponseData {
    private DelegateMetaInfo delegateMetaInfo;
    private String projectName;
    private String planName;
    private String planUrl;
    private String buildStatus;
    private String buildUrl;
    private String buildNumber;
    private ExecutionStatus executionStatus;
    private String errorMessage;
    private List<ParameterEntry> parameters;
    private List<FilePathAssertionEntry> filePathAssertionMap;
  }
}
