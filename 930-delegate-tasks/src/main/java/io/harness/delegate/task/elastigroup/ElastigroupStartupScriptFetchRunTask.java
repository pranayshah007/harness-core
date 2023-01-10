/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.elastigroup.request.ElastigroupStartupScriptFetchRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupStartupScriptFetchResponse;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupStartupScriptFetchRunTask extends AbstractDelegateRunnableTask {
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private GitFetchTaskHelper gitFetchTaskHelper;

  public ElastigroupStartupScriptFetchRunTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
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
      ElastigroupStartupScriptFetchRequest elastigroupStartupScriptFetchRequest =
          (ElastigroupStartupScriptFetchRequest) parameters;

      UnitProgressData unitProgressData = UnitProgressData.builder().build();

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

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
