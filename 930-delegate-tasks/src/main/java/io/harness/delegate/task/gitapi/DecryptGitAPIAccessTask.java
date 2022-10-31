/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.gitapi;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.gitapi.DecryptGitAPIAccessTaskResponse;
import io.harness.delegate.beans.gitapi.DecryptGitAPiAccessTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@Singleton
public class DecryptGitAPIAccessTask extends AbstractDelegateRunnableTask {
  @Inject private SecretDecryptionService secretDecryptionService;

  public DecryptGitAPIAccessTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not Implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    DecryptGitAPiAccessTaskParams taskParams = (DecryptGitAPiAccessTaskParams) parameters;
    ScmConnector scmConnector = taskParams.getScmConnector();
    DecryptableEntity decrytableEntity = getDecrytableEntity(scmConnector);
    if (decrytableEntity != null) {
      secretDecryptionService.decrypt(decrytableEntity, taskParams.getEncryptedDataDetails());
    }
    return DecryptGitAPIAccessTaskResponse.builder().scmConnector(scmConnector).build();
  }

  private DecryptableEntity getDecrytableEntity(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      return getAPIAccess((GithubConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      return getAPIAccess((GitlabConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      return getAPIAccess((BitbucketConnectorDTO) scmConnector);
    }
    throw new NotImplementedException("Unsupported Git Connector type " + scmConnector.getClass());
  }

  private DecryptableEntity getAPIAccess(GithubConnectorDTO githubConnector) {
    if (githubConnector == null || githubConnector.getApiAccess() == null) {
      return null;
    }
    return githubConnector.getApiAccess().getSpec();
  }

  private DecryptableEntity getAPIAccess(GitlabConnectorDTO gitlabConnector) {
    if (gitlabConnector == null || gitlabConnector.getApiAccess() == null) {
      return null;
    }
    return gitlabConnector.getApiAccess().getSpec();
  }

  private DecryptableEntity getAPIAccess(BitbucketConnectorDTO bitbucketConnector) {
    if (bitbucketConnector == null || bitbucketConnector.getApiAccess() == null) {
      return null;
    }
    return bitbucketConnector.getApiAccess().getSpec();
  }
}
