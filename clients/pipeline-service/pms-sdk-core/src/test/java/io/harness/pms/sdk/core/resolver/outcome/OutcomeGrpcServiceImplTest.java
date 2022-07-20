/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.outcome;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.category.element.UnitTests;
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
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.data.StringOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest(OutcomeProtoServiceBlockingStub.class)
public class OutcomeGrpcServiceImplTest extends PmsSdkCoreTestBase {
  public static String PLAN_EXECUTION_ID = "planExecutionId";
  public static String RUNTIME_ID = "runtimeId";
  OutcomeProtoServiceBlockingStub outcomeProtoServiceBlockingStub;
  OutcomeGrpcServiceImpl outcomeGrpcService;

  @Before
  public void setup() {
    outcomeProtoServiceBlockingStub = Mockito.mock(OutcomeProtoServiceBlockingStub.class);
    outcomeGrpcService = new OutcomeGrpcServiceImpl(outcomeProtoServiceBlockingStub);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFindAllByRuntimeId() {
    when(outcomeProtoServiceBlockingStub.findAllByRuntimeId(OutcomeFindAllBlobRequest.newBuilder()
                                                                .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                                .setRuntimeId(RUNTIME_ID)
                                                                .build()))
        .thenReturn(OutcomeFindAllBlobResponse.newBuilder().build());
    assertThat(outcomeGrpcService.findAllByRuntimeId(PLAN_EXECUTION_ID, RUNTIME_ID).size()).isEqualTo(0);
    Mockito.verify(outcomeProtoServiceBlockingStub)
        .findAllByRuntimeId(OutcomeFindAllBlobRequest.newBuilder()
                                .setPlanExecutionId(PLAN_EXECUTION_ID)
                                .setRuntimeId(RUNTIME_ID)
                                .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchOutcomes() {
    when(outcomeProtoServiceBlockingStub.fetchOutcomes(
             OutcomeFetchOutcomesBlobRequest.newBuilder().addOutcomeInstanceIds(RUNTIME_ID).build()))
        .thenReturn(OutcomeFetchOutcomesBlobResponse.newBuilder().build());
    assertThat(outcomeGrpcService.fetchOutcomes(Arrays.asList(RUNTIME_ID)).size()).isEqualTo(0);
    Mockito.verify(outcomeProtoServiceBlockingStub)
        .fetchOutcomes(OutcomeFetchOutcomesBlobRequest.newBuilder().addOutcomeInstanceIds(RUNTIME_ID).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFetchOutcome() {
    when(outcomeProtoServiceBlockingStub.fetchOutcome(
             OutcomeFetchOutcomeBlobRequest.newBuilder().setOutcomeInstanceId(RUNTIME_ID).build()))
        .thenReturn(OutcomeFetchOutcomeBlobResponse.newBuilder().build());
    assertThat(outcomeGrpcService.fetchOutcome(RUNTIME_ID)).isNull();
    Mockito.verify(outcomeProtoServiceBlockingStub)
        .fetchOutcome(OutcomeFetchOutcomeBlobRequest.newBuilder().setOutcomeInstanceId(RUNTIME_ID).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testResolve() {
    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject("test");
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    when(outcomeProtoServiceBlockingStub.resolve(
             OutcomeResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build()))
        .thenReturn(OutcomeResolveBlobResponse.newBuilder().build());
    outcomeGrpcService.resolve(ambiance, refObject);
    Mockito.verify(outcomeProtoServiceBlockingStub)
        .resolve(OutcomeResolveBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testConsume() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    when(outcomeProtoServiceBlockingStub.consume(
             OutcomeConsumeBlobRequest.newBuilder()
                 .setAmbiance(ambiance)
                 .setName("name")
                 .setValue("")
                 .setGroupName("groupName")
                 .setValue("{\"__recast\":\"io.harness.pms.sdk.core.data.StringOutcome\"}")
                 .build()))
        .thenReturn(OutcomeConsumeBlobResponse.newBuilder().build());
    outcomeGrpcService.consume(ambiance, "name", StringOutcome.builder().build(), "groupName");
    Mockito.verify(outcomeProtoServiceBlockingStub)
        .consume(OutcomeConsumeBlobRequest.newBuilder()
                     .setAmbiance(ambiance)
                     .setName("name")
                     .setValue("{\"__recast\":\"io.harness.pms.sdk.core.data.StringOutcome\"}")
                     .setGroupName("groupName")
                     .build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testResolveOptional() {
    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject("test");
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    when(outcomeProtoServiceBlockingStub.resolveOptional(
             OutcomeResolveOptionalBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build()))
        .thenReturn(OutcomeResolveOptionalBlobResponse.newBuilder().build());
    outcomeGrpcService.resolveOptional(ambiance, refObject);
    Mockito.verify(outcomeProtoServiceBlockingStub)
        .resolveOptional(
            OutcomeResolveOptionalBlobRequest.newBuilder().setAmbiance(ambiance).setRefObject(refObject).build());
  }
}
