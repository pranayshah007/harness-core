/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.expression;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.ExpressionRequest;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.sdk.core.registries.FunctorRegistry;
import io.harness.rule.Owner;

import io.grpc.stub.StreamObserver;
import java.util.Arrays;
import lombok.Builder;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RemoteFunctorServiceTest extends CategoryTest {
  @Mock FunctorRegistry functorRegistry;
  @Mock ExceptionManager exceptionManager;
  @InjectMocks RemoteFunctorService remoteFunctorService;
  String responseYaml = "{\"value\":\"dummy\"}";
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEvaluate() {
    DummySdkFunctor functor = new DummySdkFunctor(new String[] {});
    doReturn(functor).when(functorRegistry).obtain("dummy");
    Ambiance ambiance = Ambiance.newBuilder().build();
    ExpressionRequest expressionRequest = ExpressionRequest.newBuilder()
                                              .setFunctorKey("dummy")
                                              .setAmbiance(ambiance)
                                              .addAllArgs(Arrays.asList("arg1", "arg2"))
                                              .build();
    DummyStreamObserver<ExpressionResponse> responseObserver = new DummyStreamObserver<>();

    remoteFunctorService.evaluate(expressionRequest, responseObserver);
    assertNotNull(responseObserver.getExpressionResponse());
    assertEquals(responseObserver.getExpressionResponse().getValue(), responseYaml);
    assertEquals(functor.getArgsList()[0], "arg1");
    assertEquals(functor.getArgsList()[1], "arg2");

    when(functorRegistry.obtain("dummy")).thenThrow(new InvalidRequestException("Functor not present"));
    doReturn(new InvalidRequestException("Functor not present")).when(exceptionManager).processException(any());
    remoteFunctorService.evaluate(expressionRequest, responseObserver);
    assertEquals(responseObserver.getExpressionResponse().getErrorResponse().getMessages(0), "INVALID_REQUEST");
  }

  @Data
  private static class DummyStreamObserver<T> implements StreamObserver<T> {
    ExpressionResponse expressionResponse;
    @Override
    public void onNext(Object o) {
      expressionResponse = (ExpressionResponse) o;
    }
    @Override
    public void onError(Throwable throwable) {}
    @Override
    public void onCompleted() {}
  }

  @Data
  @Builder
  private static class DummySdkFunctor implements SdkFunctor {
    String[] argsList;
    @Override
    public ExpressionResult get(Ambiance ambiance, String... args) {
      argsList = args;
      return StringResult.builder().value("dummy").build();
    }
  }
}
