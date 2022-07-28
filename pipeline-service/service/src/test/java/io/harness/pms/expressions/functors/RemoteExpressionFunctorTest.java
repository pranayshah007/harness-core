/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
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
import java.util.Date;
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
    on(remoteExpressionFunctor).set("ambiance", ambiance);
    on(remoteExpressionFunctor).set("functorKey", "functorKey");
    on(remoteExpressionFunctor).set("remoteFunctorServiceBlockingStub", blockingStub);

    // For single string as argument
    Map<String, Object> map = (Map<String, Object>) remoteExpressionFunctor.get("empty");
    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), 1);
    assertEquals(request.getFunctorKey(), "functorKey");
    assertNotNull(map);
    assertEquals(map.get("value"), "DummyValue");

    // For array of strings as argument
    String[] allArgs = {"empty", "arg1"};
    map = (Map<String, Object>) remoteExpressionFunctor.get(allArgs);
    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), 2);
    assertEquals(request.getFunctorKey(), "functorKey");
    assertNotNull(map);
    assertEquals(map.get("value"), "DummyValue");
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
}
