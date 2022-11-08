/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.elastigroup.request.ElastigroupParametersFetchRequest;
import io.harness.delegate.task.elastigroup.request.ElastigroupStartupScriptFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupParametersFetchResponse;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.secret.SecretSanitizerThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupParametersFetchTask extends AbstractDelegateRunnableTask {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;

  public ElastigroupParametersFetchTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
                                        Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      ElastigroupParametersFetchRequest elastigroupParametersFetchRequest = (ElastigroupParametersFetchRequest) parameters;

      UnitProgressData unitProgressData =
              getCommandUnitProgressData(ElastigroupCommandUnitConstants.fetchElastigroupJson.toString(), CommandExecutionStatus.SUCCESS);

      return ElastigroupParametersFetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .unitProgressData(unitProgressData)
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in elastigroup parameters fetch task", sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }

  public UnitProgressData getCommandUnitProgressData(
          String commandName, CommandExecutionStatus commandExecutionStatus) {
    LinkedHashMap<String, CommandUnitProgress> commandUnitProgressMap = new LinkedHashMap<>();
    CommandUnitProgress commandUnitProgress = CommandUnitProgress.builder().status(commandExecutionStatus).build();
    commandUnitProgressMap.put(commandName, commandUnitProgress);
    CommandUnitsProgress commandUnitsProgress =
            CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressMap).build();
    return UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress);
  }


  public boolean isSupportingErrorFramework() {
    return true;
  }
}
