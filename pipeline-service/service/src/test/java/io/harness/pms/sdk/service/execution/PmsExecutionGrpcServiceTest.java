/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.service.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.service.ExecutionSummaryResponse;
import io.harness.pms.contracts.service.ExecutionSummaryUpdateRequest;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;

import io.grpc.stub.StreamObserver;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class PmsExecutionGrpcServiceTest extends CategoryTest {
  @Mock PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Spy @InjectMocks PmsExecutionGrpcService pmsExecutionGrpcService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateExecutionSummary() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest = ExecutionSummaryUpdateRequest.newBuilder().build();
    DummyStreamObserver<ExecutionSummaryResponse> responseObserver = new DummyStreamObserver<>();
    pmsExecutionGrpcService.updateExecutionSummary(executionSummaryUpdateRequest, responseObserver);
    verify(pmsExecutionGrpcService, times(1)).updatePipelineInfoJson(any());
    verify(pmsExecutionGrpcService, times(1)).updateStageModuleInfo(any());
    assertThat(responseObserver.executionSummaryResponse).isNotNull();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoJson() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("pms")
            .setPlanExecutionId("planExecutionId")
            .setPipelineModuleInfoJson("{\"moduleInfo\" : {\n"
                + "        \"pms\" : {\n"
                + "            \"__recast\" : \"io.harness.pms.plan.execution.PmsPipelineModuleInfo\",\n"
                + "            \"approvalStageNames\" : [\n"
                + "                \"s1\"\n"
                + "            ],\n"
                + "            \"hasApprovalStage\" : true\n"
                + "        }\n"
                + "    }}")
            .build();
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().planExecutionId("planExecutionId").build();
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionSummaryRepository).update(any(), any());
    pmsExecutionGrpcService.updatePipelineInfoJson(executionSummaryUpdateRequest);
    verify(pmsExecutionSummaryRepository, times(1)).update(any(), any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testUpdateStageModuleInfo() {
    ExecutionSummaryUpdateRequest executionSummaryUpdateRequest =
        ExecutionSummaryUpdateRequest.newBuilder()
            .setModuleName("pms")
            .setNodeUuid("123456")
            .setPlanExecutionId("planExecutionId")
            .setNodeModuleInfoJson("{\n"
                + "    \"lomivDmmQN6EWfXfUlt_Qw\" : {\n"
                + "        \"nodeType\" : \"Approval\",\n"
                + "        \"nodeGroup\" : \"STAGE\",\n"
                + "        \"nodeIdentifier\" : \"s1\",\n"
                + "        \"name\" : \"s1\",\n"
                + "        \"nodeUuid\" : \"lomivDmmQN6EWfXfUlt_Qw\",\n"
                + "        \"status\" : \"ABORTED\",\n"
                + "        \"module\" : \"pms\",\n"
                + "        \"moduleInfo\" : {\n"
                + "            \"pms\" : {\n"
                + "                \"__recast\" : \"io.harness.pms.plan.execution.PmsExecutionServiceInfoProvider$PmsNoopModuleInfo\"\n"
                + "            }\n"
                + "        }\n"
                + "}\n"
                + "}")
            .build();

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().planExecutionId("planExecutionId").build();
    doReturn(pipelineExecutionSummaryEntity).when(pmsExecutionSummaryRepository).update(any(), any());
    pmsExecutionGrpcService.updateStageModuleInfo(executionSummaryUpdateRequest);
    verify(pmsExecutionSummaryRepository, times(1)).update(any(), any());
  }

  @Data
  private static class DummyStreamObserver<T> implements StreamObserver<T> {
    ExecutionSummaryResponse executionSummaryResponse;
    @Override
    public void onNext(Object o) {
      executionSummaryResponse = (ExecutionSummaryResponse) o;
    }
    @Override
    public void onError(Throwable throwable) {}
    @Override
    public void onCompleted() {}
  }
}
