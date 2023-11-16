/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.resolver.expressions;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.service.EngineExpressionProtoServiceGrpc.EngineExpressionProtoServiceBlockingStub;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobRequest;
import io.harness.pms.contracts.service.ExpressionEvaluateBlobResponse;
import io.harness.pms.contracts.service.ExpressionRenderBlobRequest;
import io.harness.pms.contracts.service.ExpressionRenderBlobResponse;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest(EngineExpressionProtoServiceBlockingStub.class)
public class EngineGrpcExpressionServiceTest extends PmsSdkCoreTestBase {
  EngineExpressionProtoServiceBlockingStub engineExpressionProtoServiceBlockingStub;
  EngineGrpcExpressionService engineGrpcExpressionService;

  @Before
  public void beforeTest() {
    engineExpressionProtoServiceBlockingStub = Mockito.mock(EngineExpressionProtoServiceBlockingStub.class);
    engineGrpcExpressionService = new EngineGrpcExpressionService(engineExpressionProtoServiceBlockingStub);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRenderExpression() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String expression = "test";
    assertThatThrownBy(() -> engineGrpcExpressionService.renderExpression(ambiance, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The expression cannot be empty.");
    assertThatThrownBy(() -> engineGrpcExpressionService.renderExpression(ambiance, "", false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The expression cannot be empty.");

    ExpressionRenderBlobResponse expressionRenderBlobResponse =
        ExpressionRenderBlobResponse.newBuilder().setValue("test").build();
    Mockito
        .when(engineExpressionProtoServiceBlockingStub.renderExpression(ExpressionRenderBlobRequest.newBuilder()
                                                                            .setAmbiance(ambiance)
                                                                            .setExpression(expression)
                                                                            .setSkipUnresolvedExpressionsCheck(false)
                                                                            .build()))
        .thenReturn(expressionRenderBlobResponse);
    assertThat(engineGrpcExpressionService.renderExpression(ambiance, expression, false)).isEqualTo("test");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testEvaluateExpression() {
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    String expression = "{'test':'test'}";
    assertThatThrownBy(
        () -> engineGrpcExpressionService.evaluateExpression(ambiance, null, ExpressionMode.UNKNOWN_MODE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The expression cannot be empty.");
    assertThatThrownBy(() -> engineGrpcExpressionService.evaluateExpression(ambiance, "", ExpressionMode.UNKNOWN_MODE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The expression cannot be empty.");

    ExpressionEvaluateBlobResponse expressionRenderBlobResponse = ExpressionEvaluateBlobResponse.newBuilder().build();
    Mockito
        .when(engineExpressionProtoServiceBlockingStub.evaluateExpression(
            ExpressionEvaluateBlobRequest.newBuilder().setAmbiance(ambiance).setExpression(expression).build()))
        .thenReturn(expressionRenderBlobResponse);
    assertThat(engineGrpcExpressionService.evaluateExpression(ambiance, expression))
        .isEqualTo(RecastOrchestrationUtils.fromJson(null, Object.class));

    String expression1 = "{'test1':'test1'}";
    ExpressionMode expressionMode = ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;

    Mockito
        .when(engineExpressionProtoServiceBlockingStub.evaluateExpression(ExpressionEvaluateBlobRequest.newBuilder()
                                                                              .setAmbiance(ambiance)
                                                                              .setExpressionMode(expressionMode)
                                                                              .setExpression(expression1)
                                                                              .setNewRecastFlow(true)
                                                                              .build()))
        .thenReturn(expressionRenderBlobResponse);
    assertThat(engineGrpcExpressionService.evaluateExpression(ambiance, expression1, expressionMode))
        .isEqualTo(RecastOrchestrationUtils.fromJson(null, Object.class, true));
  }
}
