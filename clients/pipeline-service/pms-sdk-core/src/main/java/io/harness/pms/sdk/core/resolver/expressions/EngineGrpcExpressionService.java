/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.service.EngineExpressionProtoServiceGrpc.EngineExpressionProtoServiceBlockingStub;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobRequest;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobResponse;
import io.harness.pms.contracts.service.ExpressionRenderBlobRequest;
import io.harness.pms.contracts.service.ExpressionRenderBlobResponse;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.PmsGrpcClientUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class EngineGrpcExpressionService implements EngineExpressionService {
  private final EngineExpressionProtoServiceBlockingStub engineExpressionProtoServiceBlockingStub;

  @Inject
  public EngineGrpcExpressionService(
      EngineExpressionProtoServiceBlockingStub engineExpressionProtoServiceBlockingStub) {
    this.engineExpressionProtoServiceBlockingStub = engineExpressionProtoServiceBlockingStub;
  }

  @Override
  public String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck) {
    ExpressionRenderBlobResponse expressionRenderBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(engineExpressionProtoServiceBlockingStub::renderExpression,
            ExpressionRenderBlobRequest.newBuilder()
                .setAmbiance(ambiance)
                .setExpression(expression)
                .setSkipUnresolvedExpressionsCheck(skipUnresolvedExpressionsCheck)
                .build());
    return expressionRenderBlobResponse.getValue();
  }

  @Override
  public String renderExpression(Ambiance ambiance, String expression, ExpressionMode mode) {
    Preconditions.checkNotNull(mode);
    Preconditions.checkArgument(mode != ExpressionMode.UNKNOWN_MODE, "mode cannot be set to unknown");

    ExpressionRenderBlobResponse expressionRenderBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(engineExpressionProtoServiceBlockingStub::renderExpression,
            ExpressionRenderBlobRequest.newBuilder()
                .setAmbiance(ambiance)
                .setExpression(expression)
                .setExpressionMode(mode)
                .build());
    return expressionRenderBlobResponse.getValue();
  }

  @Override
  public Object evaluateExpression(Ambiance ambiance, String expression) {
    ExpressionEvaluateBlobResponse expressionEvaluateBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(engineExpressionProtoServiceBlockingStub::evaluateExpression,
            ExpressionEvaluateBlobRequest.newBuilder().setAmbiance(ambiance).setExpression(expression).build());
    return RecastOrchestrationUtils.fromJson(expressionEvaluateBlobResponse.getValue(), Object.class);
  }
  @Override
  public Object evaluateExpression(Ambiance ambiance, String expression, ExpressionMode mode) {
    ExpressionEvaluateBlobResponse expressionEvaluateBlobResponse =
        PmsGrpcClientUtils.retryAndProcessException(engineExpressionProtoServiceBlockingStub::evaluateExpression,
            ExpressionEvaluateBlobRequest.newBuilder()
                .setAmbiance(ambiance)
                .setExpression(expression)
                .setExpressionMode(mode)
                .setNewRecastFlow(true)
                .build());
    return RecastOrchestrationUtils.fromJson(expressionEvaluateBlobResponse.getValue(), Object.class, true);
  }
}
