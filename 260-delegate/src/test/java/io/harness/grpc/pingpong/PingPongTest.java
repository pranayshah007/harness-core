/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.pingpong;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.category.element.UnitTests;
import io.harness.delegate.service.DelegateAgentServiceImpl;
import io.harness.event.PingPongServiceGrpc;
import io.harness.event.PingPongServiceGrpc.PingPongServiceBlockingStub;
import io.harness.grpc.auth.DelegateAuthCallCredentials;
import io.harness.grpc.auth.DelegateAuthServerInterceptor;
import io.harness.rule.Owner;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.security.TokenGenerator;

import software.wings.service.impl.DelegateConnectionDao;

import io.grpc.Channel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.time.Instant;
import lombok.val;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;

@RunWith(MockitoJUnitRunner.class)
public class PingPongTest extends CategoryTest implements MockableTestMixin {
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private PingPongClient pingPongClient;
  private Server server;
  private Logger mockClientLogger;
  private Logger mockServerLogger;

  @Before
  public void setUp() throws Exception {
    DelegateConnectionDao delegateConnectionDao;
    mockClientLogger = mock(Logger.class);
    mockServerLogger = mock(Logger.class);
    delegateConnectionDao = mock(DelegateConnectionDao.class);
    setStaticFieldValue(PingPongClient.class, "log", mockClientLogger);
    setStaticFieldValue(PingPongService.class, "log", mockServerLogger);
    setStaticFieldValue(DelegateAgentServiceImpl.class, "delegateId", "DELEGATE_ID");
    String serverName = InProcessServerBuilder.generateName();
    Channel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).build());
    final TokenGenerator tokenGenerator = mock(TokenGenerator.class);
    when(tokenGenerator.getToken(anyString(), anyString())).thenReturn("TOKEN");
    PingPongServiceBlockingStub pingPongServiceBlockingStub =
        PingPongServiceGrpc.newBlockingStub(channel).withCallCredentials(
            new DelegateAuthCallCredentials(tokenGenerator, "ACCOUNT_ID", false));
    pingPongClient = new PingPongClient(pingPongServiceBlockingStub, "VERSION");
    server = InProcessServerBuilder.forName(serverName)
                 .directExecutor()
                 .addService(new PingPongService(delegateConnectionDao))
                 .intercept(new DelegateAuthServerInterceptor(mock(DelegateTokenAuthenticator.class)))
                 .build()
                 .start();
    grpcCleanup.register(server);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldLogPingSuccessOnClient() throws Exception {
    pingPongClient.runOneIteration();
    val captor = ArgumentCaptor.forClass(String.class);
    verify(mockClientLogger).info(captor.capture(), any(Instant.class));
    assertThat(captor.getValue()).matches("Ping at .* successful");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldLogPingSuccessOnServer() throws Exception {
    pingPongClient.runOneIteration();
    val captor = ArgumentCaptor.forClass(String.class);
    verify(mockServerLogger).info(captor.capture(), any(), any(), any(), any());
    assertThat(captor.getValue()).matches("Ping at .* received .*");
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldLogPingFailure() throws Exception {
    server.shutdownNow().awaitTermination();
    pingPongClient.runOneIteration();
    val captor = ArgumentCaptor.forClass(String.class);
    verify(mockClientLogger).error(captor.capture(), any(Throwable.class));
    assertThat(captor.getValue()).isEqualTo("Ping failed");
  }
}
