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
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ecs.EcsGitFetchRunTaskFileConfig;
import io.harness.delegate.task.ecs.request.EcsGitFetchRunTaskRequest;
import io.harness.delegate.task.ecs.response.EcsGitFetchRunTaskResponse;
import io.harness.delegate.task.elastigroup.request.ElastigroupStartupScriptFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.secret.SecretSanitizerThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static java.lang.String.format;
import static software.wings.beans.LogColor.Red;
import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupStartupScriptFetchRunTask extends AbstractDelegateRunnableTask {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;

  public ElastigroupStartupScriptFetchRunTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
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
      ElastigroupStartupScriptFetchRequest elastigroupStartupScriptFetchRequest = (ElastigroupStartupScriptFetchRequest) parameters;

      UnitProgressData unitProgressData =
              getCommandUnitProgressData(ElastigroupCommandUnitConstants.fetchStartupScript.toString(), CommandExecutionStatus.SUCCESS);

      return ElastigroupStartupScriptFetchResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
              .startupScript(elastigroupStartupScriptFetchRequest.getStartupScript())
          .unitProgressData(unitProgressData)
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in elastigroup run task startup script fetch task", sanitizedException);
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
