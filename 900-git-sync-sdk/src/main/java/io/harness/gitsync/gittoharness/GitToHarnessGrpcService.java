/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gittoharness;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.AuthorizationServiceHeader;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.ChangeSet;
import io.harness.gitsync.GitToHarnessProcessRequest;
import io.harness.gitsync.GitToHarnessServiceGrpc.GitToHarnessServiceImplBase;
import io.harness.gitsync.MarkEntityInvalidRequest;
import io.harness.gitsync.MarkEntityInvalidResponse;
import io.harness.gitsync.ProcessingResponse;
import io.harness.gitsync.ResetGitSyncSDKCacheRequest;
import io.harness.gitsync.ResetGitSyncSDKCacheResponse;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DX)
public class GitToHarnessGrpcService extends GitToHarnessServiceImplBase {
  @Inject @Named("GitSdkAuthorizationServiceHeader") AuthorizationServiceHeader authorizationServiceHeader;
  @Inject GitToHarnessSdkProcessor gitToHarnessSdkProcessor;

  @Override
  public void syncRequestFromGit(ChangeSet request, StreamObserver<ProcessingResponse> responseObserver) {
    log.info("grpc request done");
    responseObserver.onNext(ProcessingResponse.newBuilder().build());
    responseObserver.onCompleted();
  }

  @Override
  public void process(
      GitToHarnessProcessRequest gitToHarnessRequest, StreamObserver<ProcessingResponse> responseObserver) {
    // todo: add proper ids so that we can check the git flows
    log.info("Grpc request recieved");
    try {
      log.info("AuthorizationServiceHeader value {}", authorizationServiceHeader);
      SecurityContextBuilder.setContext(new ServicePrincipal(authorizationServiceHeader.getServiceId()));
      ProcessingResponse processingResponse =
          gitToHarnessSdkProcessor.gitToHarnessProcessingRequest(gitToHarnessRequest);
      responseObserver.onNext(processingResponse);
      responseObserver.onCompleted();
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
    log.info("Grpc request completed");
  }

  @Override
  public void markEntitiesInvalid(
      MarkEntityInvalidRequest request, StreamObserver<MarkEntityInvalidResponse> responseObserver) {
    responseObserver.onNext(gitToHarnessSdkProcessor.markEntitiesInvalid(request));
    responseObserver.onCompleted();
  }

  @Override
  public void resetGitSyncSDKCache(
      ResetGitSyncSDKCacheRequest request, StreamObserver<ResetGitSyncSDKCacheResponse> responseObserver) {
    responseObserver.onNext(gitToHarnessSdkProcessor.resetGitSyncSDKCache(request));
    responseObserver.onCompleted();
  }
}
