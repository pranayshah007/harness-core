/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.outcome;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.service.OutcomeConsumeBlobRequest;
import io.harness.pms.contracts.service.OutcomeConsumeBlobResponse;
import io.harness.pms.contracts.service.OutcomeFetchOutcomeBlobRequest;
import io.harness.pms.contracts.service.OutcomeFetchOutcomeBlobResponse;
import io.harness.pms.contracts.service.OutcomeFetchOutcomesBlobRequest;
import io.harness.pms.contracts.service.OutcomeFetchOutcomesBlobResponse;
import io.harness.pms.contracts.service.OutcomeFindAllBlobRequest;
import io.harness.pms.contracts.service.OutcomeFindAllBlobResponse;
import io.harness.pms.contracts.service.OutcomeProtoServiceGrpc.OutcomeProtoServiceBlockingStub;
import io.harness.pms.contracts.service.OutcomeResolveBlobRequest;
import io.harness.pms.contracts.service.OutcomeResolveBlobResponse;
import io.harness.pms.contracts.service.OutcomeResolveOptionalBlobRequest;
import io.harness.pms.contracts.service.OutcomeResolveOptionalBlobResponse;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.pms.utils.PmsGrpcClientUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.NonNull;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OutcomeGrpcServiceImpl implements OutcomeService {
  private final OutcomeProtoServiceBlockingStub outcomeProtoServiceBlockingStub;

  @Inject
  public OutcomeGrpcServiceImpl(OutcomeProtoServiceBlockingStub outcomeProtoServiceBlockingStub) {
    this.outcomeProtoServiceBlockingStub = outcomeProtoServiceBlockingStub;
  }

  @Override
  public List<Outcome> findAllByRuntimeId(String planExecutionId, String runtimeId) {
    OutcomeFindAllBlobResponse allByRuntimeId =
        PmsGrpcClientUtils.retryAndProcessException(outcomeProtoServiceBlockingStub::findAllByRuntimeId,
            OutcomeFindAllBlobRequest.newBuilder().setPlanExecutionId(planExecutionId).setRuntimeId(runtimeId).build());
    return PmsOutcomeMapper.convertJsonToOutcome(allByRuntimeId.getOutcomesList());
  }

  @Override
  public List<Outcome> fetchOutcomes(List<String> outcomeInstanceIds) {
    OutcomeFetchOutcomesBlobResponse outcomeFetchOutcomesBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(outcomeProtoServiceBlockingStub::fetchOutcomes,
            OutcomeFetchOutcomesBlobRequest.newBuilder().addAllOutcomeInstanceIds(outcomeInstanceIds).build());
    return PmsOutcomeMapper.convertJsonToOutcome(outcomeFetchOutcomesBlobResponse.getOutcomesList());
  }

  @Override
  public Outcome fetchOutcome(@NonNull String outcomeInstanceId) {
    OutcomeFetchOutcomeBlobResponse outcomeFetchOutcomeBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(outcomeProtoServiceBlockingStub::fetchOutcome,
            OutcomeFetchOutcomeBlobRequest.newBuilder().setOutcomeInstanceId(outcomeInstanceId).build());
    return PmsOutcomeMapper.convertJsonToOutcome(outcomeFetchOutcomeBlobResponse.getOutcome());
  }

  @Override
  public Outcome resolve(Ambiance ambiance, RefObject refObject) {
    OutcomeResolveBlobResponse resolve =
        PmsGrpcClientUtils.retryAndProcessException(outcomeProtoServiceBlockingStub::resolve,
            OutcomeResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
    return PmsOutcomeMapper.convertJsonToOutcome(resolve.getStepTransput());
  }

  @Override
  public String consume(Ambiance ambiance, String name, Outcome value, String groupName) {
    OutcomeConsumeBlobResponse response =
        PmsGrpcClientUtils.retryAndProcessException(outcomeProtoServiceBlockingStub::consume,
            OutcomeConsumeBlobRequest.newBuilder()
                .setAmbiance(ambiance)
                .setName(name)
                .setValue(PmsOutcomeMapper.convertOutcomeValueToJson(value))
                .setGroupName(groupName)
                .build());
    return response.getResponse();
  }

  @Override
  public OptionalOutcome resolveOptional(Ambiance ambiance, RefObject refObject) {
    OutcomeResolveOptionalBlobResponse response =
        PmsGrpcClientUtils.retryAndProcessException(outcomeProtoServiceBlockingStub::resolveOptional,
            OutcomeResolveOptionalBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
    return OptionalOutcome.builder()
        .found(response.getFound())
        .outcome(PmsOutcomeMapper.convertJsonToOutcome(response.getOutcome()))
        .build();
  }
}
