/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.data.service.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.pms.contracts.service.EngineExpressionProtoServiceGrpc.EngineExpressionProtoServiceImplBase;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobRequest;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobResponse;
import io.harness.pms.contracts.service.ExpressionRenderBlobRequest;
import io.harness.pms.contracts.service.ExpressionRenderBlobResponse;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class EngineExpressionGrpcServiceImpl extends EngineExpressionProtoServiceImplBase {
  private final PmsEngineExpressionService pmsEngineExpressionService;

  @Inject
  public EngineExpressionGrpcServiceImpl(PmsEngineExpressionService pmsEngineExpressionService) {
    this.pmsEngineExpressionService = pmsEngineExpressionService;
  }

  @Override
  public void renderExpression(
      ExpressionRenderBlobRequest request, StreamObserver<ExpressionRenderBlobResponse> responseObserver) {
    String value = pmsEngineExpressionService.renderExpression(
        request.getAmbiance(), request.getExpression(), request.getSkipUnresolvedExpressionsCheck());
    responseObserver.onNext(ExpressionRenderBlobResponse.newBuilder().setValue(value).build());
    responseObserver.onCompleted();
  }

  @Override
  public void evaluateExpression(
      ExpressionEvaluateBlobRequest request, StreamObserver<ExpressionEvaluateBlobResponse> responseObserver) {
    String value = pmsEngineExpressionService.evaluateExpression(request.getAmbiance(), request.getExpression());
    responseObserver.onNext(ExpressionEvaluateBlobResponse.newBuilder().setValue(value).build());
    responseObserver.onCompleted();
  }
}
