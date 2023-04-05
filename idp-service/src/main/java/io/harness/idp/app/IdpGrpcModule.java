/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcInProcessServer;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.grpc.Channel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpGrpcModule extends AbstractModule {
  private static IdpGrpcModule instance;
  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  public static IdpGrpcModule getInstance() {
    if (instance == null) {
      instance = new IdpGrpcModule();
    }
    return instance;
  }

  @Provides
  @Singleton
  public HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub gitManagerGrpcClient(
      IdpConfiguration configuration) throws SSLException {
    return HarnessToGitPushInfoServiceGrpc.newBlockingStub(
        getChannel(configuration.getGitManagerGrpcClientConfig(), configuration.getGrpcNegotiationType()));
  }

  private Channel getChannel(GrpcClientConfig clientConfig, NegotiationType grpcNegotiationType) throws SSLException {
    String authorityToUse = clientConfig.getAuthority();
    Channel channel;

    if (shouldUsePlainTextNegotiationType(grpcNegotiationType, deployMode)) {
      channel = NettyChannelBuilder.forTarget(clientConfig.getTarget())
                    .overrideAuthority(authorityToUse)
                    .usePlaintext()
                    .maxInboundMessageSize(GrpcInProcessServer.GRPC_MAXIMUM_MESSAGE_SIZE)
                    .build();
    } else {
      SslContext sslContext = GrpcSslContexts.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
      channel = NettyChannelBuilder.forTarget(clientConfig.getTarget())
                    .overrideAuthority(authorityToUse)
                    .sslContext(sslContext)
                    .maxInboundMessageSize(GrpcInProcessServer.GRPC_MAXIMUM_MESSAGE_SIZE)
                    .build();
    }

    return channel;
  }

  @VisibleForTesting
  boolean shouldUsePlainTextNegotiationType(NegotiationType negotiationType, String deployMode) {
    return "ONPREM".equals(deployMode) || "KUBERNETES_ONPREM".equals(deployMode)
        || (negotiationType != null && negotiationType.equals(NegotiationType.PLAINTEXT));
  }
}
