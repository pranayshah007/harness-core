/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jose4j.lang.JoseException;

public class ScmRefreshTokenTask extends AbstractDelegateRunnableTask {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject ScmDelegateClient scmDelegateClient;
  @Inject ScmServiceClient scmServiceClient;
  @Inject private GitDecryptionHelper gitDecryptionHelper;

  public ScmRefreshTokenTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    ScmRefreshTokenTaskParams refreshTokenTaskParams = (ScmRefreshTokenTaskParams) parameters;
    GitConfigDTO gitConfig = ScmConnectorMapper.toGitConfigDTO(refreshTokenTaskParams.getGitConfig());
    gitDecryptionHelper.decryptGitConfig(gitConfig, refreshTokenTaskParams.getEncryptionDetails());
    GitHTTPAuthenticationDTO gitHTTPAuthenticationDTO = (GitHTTPAuthenticationDTO) gitConfig.getGitAuth();

    RefreshTokenResponse response = RefreshTokenResponse.newBuilder().build();

    return ScmRefreshTokenTaskResponse.builder()
        .refreshToken(response.getRefreshToken())
        .accessToken(response.getAccessToken())
        .build();
  }
}
