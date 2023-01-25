/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;

import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.BambooRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.service.intfc.BambooBuildService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class BambooArtifactTaskHandler extends DelegateArtifactTaskHandler<BambooArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  @Inject private BambooBuildService bambooBuildService;
  @Inject @Named("bambooExecutor") private ExecutorService bambooExecutor;

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(BambooArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = bambooBuildService.validateArtifactServer(
        BambooRequestResponseMapper.toBambooConfig(attributesRequest), attributesRequest.getEncryptedDataDetails());
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public void decryptRequestDTOs(BambooArtifactDelegateRequest bambooArtifactDelegateRequest) {
    if (bambooArtifactDelegateRequest.getBambooConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(bambooArtifactDelegateRequest.getBambooConnectorDTO().getAuth().getCredentials(),
          bambooArtifactDelegateRequest.getEncryptedDataDetails());
    }
  }
}
