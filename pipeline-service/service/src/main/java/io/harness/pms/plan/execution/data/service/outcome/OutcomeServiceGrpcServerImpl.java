/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.outcome;

import io.harness.engine.pms.data.OptionalOutcome;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.pms.contracts.service.OutcomeConsumeBlobRequest;
import io.harness.pms.contracts.service.OutcomeConsumeBlobResponse;
import io.harness.pms.contracts.service.OutcomeFetchOutcomeBlobRequest;
import io.harness.pms.contracts.service.OutcomeFetchOutcomeBlobResponse;
import io.harness.pms.contracts.service.OutcomeFetchOutcomesBlobRequest;
import io.harness.pms.contracts.service.OutcomeFetchOutcomesBlobResponse;
import io.harness.pms.contracts.service.OutcomeFindAllBlobRequest;
import io.harness.pms.contracts.service.OutcomeFindAllBlobResponse;
import io.harness.pms.contracts.service.OutcomeProtoServiceGrpc.OutcomeProtoServiceImplBase;
import io.harness.pms.contracts.service.OutcomeResolveBlobRequest;
import io.harness.pms.contracts.service.OutcomeResolveBlobResponse;
import io.harness.pms.contracts.service.OutcomeResolveOptionalBlobRequest;
import io.harness.pms.contracts.service.OutcomeResolveOptionalBlobResponse;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import java.util.List;

// TODO (archit) : Right now this is acting just as a wrapper for #PmsSweepingOutputService. It also mark the module
// for understanding. Merge the two later
public class OutcomeServiceGrpcServerImpl extends OutcomeProtoServiceImplBase {
  private final PmsOutcomeService pmsOutcomeService;

  @Inject
  public OutcomeServiceGrpcServerImpl(PmsOutcomeService pmsOutcomeService) {
    this.pmsOutcomeService = pmsOutcomeService;
  }

  @Override
  public void resolve(OutcomeResolveBlobRequest request, StreamObserver<OutcomeResolveBlobResponse> responseObserver) {
    String resolve = pmsOutcomeService.resolve(request.getAmbiance(), request.getRefObject());
    responseObserver.onNext(OutcomeResolveBlobResponse.newBuilder().setStepTransput(resolve).build());
    responseObserver.onCompleted();
  }

  @Override
  public void consume(OutcomeConsumeBlobRequest request, StreamObserver<OutcomeConsumeBlobResponse> responseObserver) {
    String consume =
        pmsOutcomeService.consume(request.getAmbiance(), request.getName(), request.getValue(), request.getGroupName());
    responseObserver.onNext(OutcomeConsumeBlobResponse.newBuilder().setResponse(consume).build());
    responseObserver.onCompleted();
  }

  @Override
  public void findAllByRuntimeId(
      OutcomeFindAllBlobRequest request, StreamObserver<OutcomeFindAllBlobResponse> responseObserver) {
    List<String> allByRuntimeId =
        pmsOutcomeService.findAllByRuntimeId(request.getPlanExecutionId(), request.getRuntimeId());
    responseObserver.onNext(OutcomeFindAllBlobResponse.newBuilder().addAllOutcomes(allByRuntimeId).build());
    responseObserver.onCompleted();
  }

  @Override
  public void fetchOutcomes(
      OutcomeFetchOutcomesBlobRequest request, StreamObserver<OutcomeFetchOutcomesBlobResponse> responseObserver) {
    List<String> outcomes = pmsOutcomeService.fetchOutcomes(request.getOutcomeInstanceIdsList());
    responseObserver.onNext(OutcomeFetchOutcomesBlobResponse.newBuilder().addAllOutcomes(outcomes).build());
    responseObserver.onCompleted();
  }

  @Override
  public void fetchOutcome(
      OutcomeFetchOutcomeBlobRequest request, StreamObserver<OutcomeFetchOutcomeBlobResponse> responseObserver) {
    String outcomeJson = pmsOutcomeService.fetchOutcome(request.getOutcomeInstanceId());
    responseObserver.onNext(OutcomeFetchOutcomeBlobResponse.newBuilder().setOutcome(outcomeJson).build());
    responseObserver.onCompleted();
  }

  @Override
  public void resolveOptional(
      OutcomeResolveOptionalBlobRequest request, StreamObserver<OutcomeResolveOptionalBlobResponse> responseObserver) {
    OptionalOutcome resolve = pmsOutcomeService.resolveOptional(request.getAmbiance(), request.getRefObject());
    OutcomeResolveOptionalBlobResponse.Builder builder =
        OutcomeResolveOptionalBlobResponse.newBuilder().setFound(resolve.isFound());
    if (resolve.isFound() && resolve.getOutcome() != null) {
      builder.setOutcome(resolve.getOutcome());
    }
    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }
}
