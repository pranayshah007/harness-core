/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.bash;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import software.wings.beans.bash.ShellScriptParameters;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.lang.JoseException;

@Slf4j
public class BashScriptTask extends AbstractDelegateRunnableTask {
  @Inject private BashScriptTaskHandler bashScriptTaskHandler;

  public BashScriptTask(final DelegateTaskPackage delegateTaskPackage, final BooleanSupplier preExecute,
      final Consumer<DelegateTaskResponse> postExecute) {
    super(delegateTaskPackage, null, postExecute, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Use the other API");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    final ShellScriptParameters scriptParameters = (ShellScriptParameters) parameters;
    log.info("Shell script task parameters: accountId - {}, appId - {}, workingDir - {}, activityId - {}",
        scriptParameters.getAccountId(), scriptParameters.getAppId(), scriptParameters.getWorkingDirectory(),
        scriptParameters.getActivityId());
    return bashScriptTaskHandler.handle(scriptParameters);
  }
}
