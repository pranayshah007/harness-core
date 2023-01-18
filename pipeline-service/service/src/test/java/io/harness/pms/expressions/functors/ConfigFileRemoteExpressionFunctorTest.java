/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.common.EntityTypeConstants.FILES;
import static io.harness.common.EntityTypeConstants.SECRETS;
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
import io.harness.rule.Owner;

import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ConfigFileRemoteExpressionFunctorTest extends CategoryTest {
  private static final int EXPECTED_NUMBER_OF_ARGUMENTS = 3;
  private RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub blockingStub;
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  private ExpressionRequest request;
  private RemoteFunctorServiceGrpc.RemoteFunctorServiceImplBase remoteFunctorServiceImplBase =
      new RemoteFunctorServiceGrpc.RemoteFunctorServiceImplBase() {
        @Override
        public void evaluate(ExpressionRequest grpcRequest, StreamObserver<ExpressionResponse> responseObserver) {
          request = grpcRequest;
          responseObserver.onNext(ExpressionResponse.newBuilder().setValue(expressionResponseJson).build());
          responseObserver.onCompleted();
        }
      };
  private String expressionResponseJson =
      "{\"__recast\":\"io.harness.pms.sdk.core.execution.expression.StringResult\",\"value\":\"DummyValue\"}";

  @InjectMocks ConfigFileRemoteExpressionFunctor configFileRemoteExpressionFunctor;

  @Before
  public void setUp() throws IOException {
    grpcCleanup.register(InProcessServerBuilder.forName("configFileRemoteExpressionFunctorTest")
                             .directExecutor()
                             .addService(remoteFunctorServiceImplBase)
                             .build()
                             .start());
    ManagedChannel chan = grpcCleanup.register(
        InProcessChannelBuilder.forName("configFileRemoteExpressionFunctorTest").directExecutor().build());
    blockingStub = RemoteFunctorServiceGrpc.newBlockingStub(chan);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAsString() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    on(configFileRemoteExpressionFunctor).set("ambiance", ambiance);
    on(configFileRemoteExpressionFunctor).set("remoteFunctorServiceBlockingStub", blockingStub);

    Map<String, Object> map = (Map<String, Object>) configFileRemoteExpressionFunctor.getAsString("/folder/filename");

    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), EXPECTED_NUMBER_OF_ARGUMENTS);
    List<String> args = Arrays.asList(request.getArgsList().toArray(new String[0]));
    assertThat(args).contains("Files", "getAsString", "/folder/filename");
    assertNotNull(map);
    assertEquals(map.get("value"), "DummyValue");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAsBase64() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    on(configFileRemoteExpressionFunctor).set("ambiance", ambiance);
    on(configFileRemoteExpressionFunctor).set("remoteFunctorServiceBlockingStub", blockingStub);

    Map<String, Object> map = (Map<String, Object>) configFileRemoteExpressionFunctor.getAsBase64("secretRef");

    assertEquals(request.getAmbiance(), ambiance);
    assertEquals(request.getArgsList().size(), EXPECTED_NUMBER_OF_ARGUMENTS);
    List<String> args = Arrays.asList(request.getArgsList().toArray(new String[0]));
    assertThat(args).contains("Secrets", "getAsBase64", "secretRef");
    assertNotNull(map);
    assertEquals(map.get("value"), "DummyValue");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetReferenceType() {
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("account:/folder")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("account:/folder/file")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("account:/folder/file1.txt")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("org:/folder")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("org:/folder/file")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("org:/folder/file1/txt")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("/folder")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("/folder/file")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("/folder/file1.txt")).isEqualTo(FILES);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("/file1.txt")).isEqualTo(FILES);

    assertThat(configFileRemoteExpressionFunctor.getReferenceType("account.secretRef")).isEqualTo(SECRETS);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("org.secretRef")).isEqualTo(SECRETS);
    assertThat(configFileRemoteExpressionFunctor.getReferenceType("secretRef")).isEqualTo(SECRETS);
  }
}
