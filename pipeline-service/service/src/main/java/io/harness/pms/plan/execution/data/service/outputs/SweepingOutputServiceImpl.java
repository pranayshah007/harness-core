/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.outputs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.pms.contracts.service.OptionalSweepingOutputResolveBlobResponse;
import io.harness.pms.contracts.service.OptionalSweepingOutputResolveBlobResponse.Builder;
import io.harness.pms.contracts.service.SweepingOutputConsumeBlobRequest;
import io.harness.pms.contracts.service.SweepingOutputConsumeBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputListRequest;
import io.harness.pms.contracts.service.SweepingOutputListResponse;
import io.harness.pms.contracts.service.SweepingOutputResolveBlobRequest;
import io.harness.pms.contracts.service.SweepingOutputResolveBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputServiceGrpc.SweepingOutputServiceImplBase;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;

// TODO (prashant) : Right now this is acting just as a wrapper for #PmsSweepingOutputService. It also mark the module
// for understanding. Merge the two later
@OwnedBy(HarnessTeam.PIPELINE)
public class SweepingOutputServiceImpl extends SweepingOutputServiceImplBase {
  private final PmsSweepingOutputService pmsSweepingOutputService;

  @Inject
  public SweepingOutputServiceImpl(PmsSweepingOutputService pmsSweepingOutputService) {
    this.pmsSweepingOutputService = pmsSweepingOutputService;
  }

  @Override
  public void resolveOptional(SweepingOutputResolveBlobRequest request,
      StreamObserver<OptionalSweepingOutputResolveBlobResponse> responseObserver) {
    RawOptionalSweepingOutput resolve =
        pmsSweepingOutputService.resolveOptional(request.getAmbiance(), request.getRefObject());

    Builder builder = OptionalSweepingOutputResolveBlobResponse.newBuilder().setFound(resolve.isFound());
    if (resolve.isFound()) {
      builder.setStepTransput(resolve.getOutput());
    }

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  @Override
  public void listOutputsUsingNodeIds(
      SweepingOutputListRequest request, StreamObserver<SweepingOutputListResponse> responseObserver) {
    List<RawOptionalSweepingOutput> resolve = pmsSweepingOutputService.findOutputsUsingNodeId(
        request.getAmbiance(), request.getName(), request.getNodeIdsList());

    List<OptionalSweepingOutputResolveBlobResponse> optionalSweepingOutputResolveBlobResponses = new ArrayList<>();
    for (RawOptionalSweepingOutput rawOptionalSweepingOutput : resolve) {
      Builder builder =
          OptionalSweepingOutputResolveBlobResponse.newBuilder().setFound(rawOptionalSweepingOutput.isFound());
      if (rawOptionalSweepingOutput.isFound()) {
        builder.setStepTransput(rawOptionalSweepingOutput.getOutput());
      }
      optionalSweepingOutputResolveBlobResponses.add(builder.build());
    }

    responseObserver.onNext(SweepingOutputListResponse.newBuilder()
                                .addAllSweepingOutputResolveBlobResponses(optionalSweepingOutputResolveBlobResponses)
                                .build());
    responseObserver.onCompleted();
  }

  @Override
  public void listOutputsUsingExecutionIds(
      SweepingOutputListRequest request, StreamObserver<SweepingOutputListResponse> responseObserver) {
    List<RawOptionalSweepingOutput> resolve = pmsSweepingOutputService.findOutputsUsingExecutionIds(
        request.getAmbiance(), request.getName(), request.getNodeIdsList());

    List<OptionalSweepingOutputResolveBlobResponse> optionalSweepingOutputResolveBlobResponses = new ArrayList<>();
    for (RawOptionalSweepingOutput rawOptionalSweepingOutput : resolve) {
      Builder builder =
          OptionalSweepingOutputResolveBlobResponse.newBuilder().setFound(rawOptionalSweepingOutput.isFound());
      if (rawOptionalSweepingOutput.isFound()) {
        builder.setStepTransput(rawOptionalSweepingOutput.getOutput());
      }
      optionalSweepingOutputResolveBlobResponses.add(builder.build());
    }

    responseObserver.onNext(SweepingOutputListResponse.newBuilder()
                                .addAllSweepingOutputResolveBlobResponses(optionalSweepingOutputResolveBlobResponses)
                                .build());
    responseObserver.onCompleted();
  }

  @Override
  public void resolve(
      SweepingOutputResolveBlobRequest request, StreamObserver<SweepingOutputResolveBlobResponse> responseObserver) {
    String resolve = pmsSweepingOutputService.resolve(request.getAmbiance(), request.getRefObject());
    responseObserver.onNext(SweepingOutputResolveBlobResponse.newBuilder().setStepTransput(resolve).build());
    responseObserver.onCompleted();
  }

  @Override
  public void consume(
      SweepingOutputConsumeBlobRequest request, StreamObserver<SweepingOutputConsumeBlobResponse> responseObserver) {
    String response = pmsSweepingOutputService.consume(
        request.getAmbiance(), request.getName(), request.getValue(), request.getGroupName());
    responseObserver.onNext(SweepingOutputConsumeBlobResponse.newBuilder().setResponse(response).build());
    responseObserver.onCompleted();
  }
}
