/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.IVAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.ExpressionRequest;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.sdk.core.execution.expression.ExpressionResultUtils;
import io.harness.rule.Owner;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RemoteExpressionFunctorTest extends CategoryTest {
  private static final int EXPECTED_NUMBER_OF_ARGUMENTS = 2;
  private static final String SECRET_REF = "secretRef";
  private static final String FUNCTOR_KEY = "functorKey";
  private static final String AMBIANCE = "ambiance";
  private static final String REMOTE_FUNCTOR_SERVICE_BLOCKING_STUB = "remoteFunctorServiceBlockingStub";
  private static final String DUMMY_VALUE = "DummyValue";
  private static final String EXPRESSION_RESPONSE_VALUE = "value";

  RemoteFunctorServiceBlockingStub blockingStub;
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  ExpressionRequest request;
  RemoteFunctorServiceGrpc.RemoteFunctorServiceImplBase remoteFunctorServiceImplBase =
      new RemoteFunctorServiceGrpc.RemoteFunctorServiceImplBase() {
        @Override
        public void evaluate(ExpressionRequest grpcRequest, StreamObserver<ExpressionResponse> responseObserver) {
          request = grpcRequest;
          responseObserver.onNext(ExpressionResponse.newBuilder().setValue(expressionResponseJson).build());
          responseObserver.onCompleted();
        }
      };
  @InjectMocks RemoteExpressionFunctor remoteExpressionFunctor;
  static String expressionResponseJson =
      "{\"__recast\":\"io.harness.pms.sdk.core.execution.expression.StringResult\",\"value\":\"DummyValue\"}";

  @Before
  public void setUp() throws IOException {
    grpcCleanup.register(InProcessServerBuilder.forName("mytest")
                             .directExecutor()
                             .addService(remoteFunctorServiceImplBase)
                             .build()
                             .start());
    ManagedChannel chan = grpcCleanup.register(InProcessChannelBuilder.forName("mytest").directExecutor().build());
    blockingStub = RemoteFunctorServiceGrpc.newBlockingStub(chan);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGet() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    on(remoteExpressionFunctor).set(AMBIANCE, ambiance);
    on(remoteExpressionFunctor).set(FUNCTOR_KEY, FUNCTOR_KEY);
    on(remoteExpressionFunctor).set(REMOTE_FUNCTOR_SERVICE_BLOCKING_STUB, blockingStub);

    // For single string as argument
    Map<String, Object> map = (Map<String, Object>) remoteExpressionFunctor.get("empty");
    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), 1);
    assertEquals(request.getFunctorKey(), FUNCTOR_KEY);
    assertNotNull(map);
    assertEquals(map.get(EXPRESSION_RESPONSE_VALUE), DUMMY_VALUE);

    // For array of strings as argument
    String[] allArgs = {"empty", "arg1"};
    map = (Map<String, Object>) remoteExpressionFunctor.get(allArgs);
    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), 2);
    assertEquals(request.getFunctorKey(), FUNCTOR_KEY);
    assertNotNull(map);
    assertEquals(map.get(EXPRESSION_RESPONSE_VALUE), DUMMY_VALUE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetPrimitiveResponse() throws ClassNotFoundException {
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10", Integer.class.getSimpleName()), 10);
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("true", Boolean.class.getSimpleName()), true);
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10", String.class.getSimpleName()), "10");
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10", Byte.class.getSimpleName()), Byte.valueOf("10"));
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10", Character.class.getSimpleName()), '1');
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10", Short.class.getSimpleName()), new Short("10"));
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10", Long.class.getSimpleName()), 10L);
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10.1", Double.class.getSimpleName()), 10.1D);
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("10.1", Float.class.getSimpleName()), 10.1F);
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("2021-09-21T10:04:19.112Z", Date.class.getSimpleName()),
        Date.from(Instant.parse("2021-09-21T10:04:19.112Z")));
    assertEquals(ExpressionResultUtils.getPrimitiveResponse(Integer.class.getName(), Class.class.getSimpleName()),
        Class.forName(Integer.class.getName()));
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("a-b-c-d-e", UUID.class.getSimpleName()),
        UUID.fromString("a-b-c-d-e"));
    assertEquals(ExpressionResultUtils.getPrimitiveResponse("uri", URI.class.getSimpleName()), URI.create("uri"));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAsString() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    on(remoteExpressionFunctor).set(AMBIANCE, ambiance);
    on(remoteExpressionFunctor).set(FUNCTOR_KEY, FUNCTOR_KEY);
    on(remoteExpressionFunctor).set(REMOTE_FUNCTOR_SERVICE_BLOCKING_STUB, blockingStub);

    Map<String, Object> map = (Map<String, Object>) remoteExpressionFunctor.getAsString("/folder/filename");

    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), EXPECTED_NUMBER_OF_ARGUMENTS);
    List<String> args = Arrays.asList(request.getArgsList().toArray(new String[0]));
    assertThat(args).contains("getAsString", "/folder/filename");
    assertNotNull(map);
    assertEquals(map.get(EXPRESSION_RESPONSE_VALUE), DUMMY_VALUE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAsBase64() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    on(remoteExpressionFunctor).set(AMBIANCE, ambiance);
    on(remoteExpressionFunctor).set(FUNCTOR_KEY, FUNCTOR_KEY);
    on(remoteExpressionFunctor).set(REMOTE_FUNCTOR_SERVICE_BLOCKING_STUB, blockingStub);

    Map<String, Object> map = (Map<String, Object>) remoteExpressionFunctor.getAsBase64(SECRET_REF);

    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), EXPECTED_NUMBER_OF_ARGUMENTS);
    List<String> args = Arrays.asList(request.getArgsList().toArray(new String[0]));
    assertThat(args).contains("getAsBase64", SECRET_REF);
    assertNotNull(map);
    assertEquals(map.get(EXPRESSION_RESPONSE_VALUE), DUMMY_VALUE);
  }
}
