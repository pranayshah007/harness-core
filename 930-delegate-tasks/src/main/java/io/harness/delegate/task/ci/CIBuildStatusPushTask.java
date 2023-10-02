/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ci;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse.Status;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ci.CIBuildPushParameters.CIBuildPushTaskType;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.git.checks.GitStatusCheckHelper;
import io.harness.git.checks.GitStatusCheckParams;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class CIBuildStatusPushTask extends AbstractDelegateRunnableTask {
  @Inject private GitStatusCheckHelper gitStatusCheckHelper;

  public CIBuildStatusPushTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (((CIBuildPushParameters) parameters).commandType == CIBuildPushTaskType.STATUS) {
      CIBuildStatusPushParameters ciBuildStatusPushParameters = (CIBuildStatusPushParameters) parameters;
      GitStatusCheckParams gitStatusCheckParams = convertParams(ciBuildStatusPushParameters);
      boolean statusSent = gitStatusCheckHelper.sendStatus(gitStatusCheckParams);
      if (statusSent) {
        return BuildStatusPushResponse.builder().status(Status.SUCCESS).build();
      } else {
        return BuildStatusPushResponse.builder().status(Status.ERROR).build();
      }
    }
    return BuildStatusPushResponse.builder().status(Status.ERROR).build();
  }

  private GitStatusCheckParams convertParams(CIBuildStatusPushParameters params) {
    return GitStatusCheckParams.builder()
        .title(params.getTitle())
        .desc(params.getDesc())
        .state(params.getState())
        .buildNumber(params.getBuildNumber())
        .detailsUrl(params.getDetailsUrl())
        .repo(params.getRepo())
        .owner(params.getOwner())
        .sha(params.getSha())
        .prNumber(params.getPrNumber())
        .identifier(params.getIdentifier())
        .target_url(params.getTarget_url())
        .userName(params.getUserName())
        .connectorDetails(params.getConnectorDetails())
        .gitSCMType(params.getGitSCMType())
        .build();
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }
}
