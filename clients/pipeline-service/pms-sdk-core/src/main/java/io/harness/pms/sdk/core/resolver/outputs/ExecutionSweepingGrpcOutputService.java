/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.outputs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.service.OptionalSweepingOutputResolveBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputConsumeBlobRequest;
import io.harness.pms.contracts.service.SweepingOutputConsumeBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputListRequest;
import io.harness.pms.contracts.service.SweepingOutputListResponse;
import io.harness.pms.contracts.service.SweepingOutputResolveBlobRequest;
import io.harness.pms.contracts.service.SweepingOutputResolveBlobResponse;
import io.harness.pms.contracts.service.SweepingOutputServiceGrpc.SweepingOutputServiceBlockingStub;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.PmsGrpcClientUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class ExecutionSweepingGrpcOutputService implements ExecutionSweepingOutputService {
  private final SweepingOutputServiceBlockingStub sweepingOutputServiceBlockingStub;

  @Inject
  public ExecutionSweepingGrpcOutputService(SweepingOutputServiceBlockingStub sweepingOutputServiceBlockingStub) {
    this.sweepingOutputServiceBlockingStub = sweepingOutputServiceBlockingStub;
  }

  @Override
  public ExecutionSweepingOutput resolve(Ambiance ambiance, RefObject refObject) {
    SweepingOutputResolveBlobResponse resolve =
        PmsGrpcClientUtils.retryAndProcessException(sweepingOutputServiceBlockingStub::resolve,
            SweepingOutputResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
    return RecastOrchestrationUtils.fromJson(resolve.getStepTransput(), ExecutionSweepingOutput.class);
  }

  @Override
  public String consume(Ambiance ambiance, String name, ExecutionSweepingOutput value, String groupName) {
    SweepingOutputConsumeBlobRequest.Builder builder =
        SweepingOutputConsumeBlobRequest.newBuilder().setAmbiance(ambiance).setName(name).setValue(
            RecastOrchestrationUtils.toJson(value));
    if (EmptyPredicate.isNotEmpty(groupName)) {
      builder.setGroupName(groupName);
    }

    SweepingOutputConsumeBlobResponse sweepingOutputConsumeBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(sweepingOutputServiceBlockingStub::consume, builder.build());
    return sweepingOutputConsumeBlobResponse.getResponse();
  }

  @Override
  public OptionalSweepingOutput resolveOptional(Ambiance ambiance, RefObject refObject) {
    OptionalSweepingOutputResolveBlobResponse resolve =
        PmsGrpcClientUtils.retryAndProcessException(sweepingOutputServiceBlockingStub::resolveOptional,
            SweepingOutputResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
    return OptionalSweepingOutput.builder()
        .output(RecastOrchestrationUtils.fromJson(resolve.getStepTransput(), ExecutionSweepingOutput.class))
        .found(resolve.getFound())
        .build();
  }

  @Override
  public Optional<Map<String, Object>> resolveFromJsonAsMap(Ambiance ambiance, RefObject refObject) {
    OptionalSweepingOutputResolveBlobResponse resolve =
        PmsGrpcClientUtils.retryAndProcessException(sweepingOutputServiceBlockingStub::resolveOptional,
            SweepingOutputResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
    if (!resolve.getFound()) {
      return Optional.empty();
    }
    return Optional.of(RecastOrchestrationUtils.fromJson(resolve.getStepTransput()));
  }

  @Override
  public List<OptionalSweepingOutput> listOutputsWithGivenNameAndSetupIds(
      Ambiance ambiance, String name, List<String> nodeIds) {
    SweepingOutputListResponse resolve =
        PmsGrpcClientUtils.retryAndProcessException(sweepingOutputServiceBlockingStub::listOutputsUsingNodeIds,
            SweepingOutputListRequest.newBuilder().setAmbiance(ambiance).setName(name).addAllNodeIds(nodeIds).build());
    List<OptionalSweepingOutput> optionalSweepingOutputs = new ArrayList<>();
    for (OptionalSweepingOutputResolveBlobResponse rawOptionalSweepingOutput :
        resolve.getSweepingOutputResolveBlobResponsesList()) {
      optionalSweepingOutputs.add(OptionalSweepingOutput.builder()
                                      .output(RecastOrchestrationUtils.fromJson(
                                          rawOptionalSweepingOutput.getStepTransput(), ExecutionSweepingOutput.class))
                                      .found(rawOptionalSweepingOutput.getFound())
                                      .build());
    }
    return optionalSweepingOutputs;
  }

  @Override
  public List<OptionalSweepingOutput> listOutputsWithGivenNameAndRuntimeIds(
      Ambiance ambiance, String name, List<String> nodeIds) {
    SweepingOutputListResponse resolve =
        PmsGrpcClientUtils.retryAndProcessException(sweepingOutputServiceBlockingStub::listOutputsUsingExecutionIds,
            SweepingOutputListRequest.newBuilder().setAmbiance(ambiance).setName(name).addAllNodeIds(nodeIds).build());
    List<OptionalSweepingOutput> optionalSweepingOutputs = new ArrayList<>();
    for (OptionalSweepingOutputResolveBlobResponse rawOptionalSweepingOutput :
        resolve.getSweepingOutputResolveBlobResponsesList()) {
      optionalSweepingOutputs.add(OptionalSweepingOutput.builder()
                                      .output(RecastOrchestrationUtils.fromJson(
                                          rawOptionalSweepingOutput.getStepTransput(), ExecutionSweepingOutput.class))
                                      .found(rawOptionalSweepingOutput.getFound())
                                      .build());
    }
    return optionalSweepingOutputs;
  }

  @Override
  public String consumeOptional(Ambiance ambiance, String name, ExecutionSweepingOutput value, String groupName) {
    SweepingOutputConsumeBlobRequest.Builder builder =
        SweepingOutputConsumeBlobRequest.newBuilder().setAmbiance(ambiance).setName(name).setValue(
            RecastOrchestrationUtils.toJson(value));
    if (EmptyPredicate.isNotEmpty(groupName)) {
      builder.setGroupName(groupName);
    }
    SweepingOutputConsumeBlobResponse sweepingOutputConsumeBlobResponse = PmsGrpcClientUtils.retryAndProcessException(
        sweepingOutputServiceBlockingStub::consumeOptional, builder.build());
    return sweepingOutputConsumeBlobResponse.getResponse();
  }
}
