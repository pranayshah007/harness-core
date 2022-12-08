/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.client;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.FullSyncServiceGrpc;
import io.harness.gitsync.FullSyncServiceGrpc.FullSyncServiceBlockingStub;
import io.harness.gitsync.GitToHarnessServiceGrpc;
import io.harness.gitsync.GitToHarnessServiceGrpc.GitToHarnessServiceBlockingStub;
import io.harness.gitsync.sdk.GitSyncGrpcConstants;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcInProcessServer;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLException;

@OwnedBy(DX)
public class GitSyncSdkGrpcClientModule extends AbstractModule {
  private static GitSyncSdkGrpcClientModule instance;
  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  public static GitSyncSdkGrpcClientModule getInstance() {
    if (instance == null) {
      instance = new GitSyncSdkGrpcClientModule();
    }
    return instance;
  }

  @Override
  protected void configure() {}

  public Channel getChannel(GrpcClientConfig clientConfig) throws SSLException {
    String authorityToUse = clientConfig.getAuthority();
    Channel channel;

    if ("ONPREM".equals(deployMode) || "KUBERNETES_ONPREM".equals(deployMode)) {
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

  @Provides
  @Singleton
  public Map<Microservice, GitToHarnessServiceBlockingStub> gitToHarnessServiceGrpcClient(
      @Named("GitSyncGrpcClientConfigs") Map<Microservice, GrpcClientConfig> clientConfigs) throws SSLException {
    Map<Microservice, GitToHarnessServiceBlockingStub> map = new HashMap<>();
    for (Map.Entry<Microservice, GrpcClientConfig> entry : clientConfigs.entrySet()) {
      map.put(entry.getKey(), GitToHarnessServiceGrpc.newBlockingStub(getChannel(entry.getValue())));
    }
    map.put(GmsClientConstants.moduleType,
        GitToHarnessServiceGrpc.newBlockingStub(
            InProcessChannelBuilder.forName(GitSyncGrpcConstants.GitSyncSdkInternalService).build()));
    return map;
  }

  @Provides
  @Singleton
  public Map<Microservice, FullSyncServiceBlockingStub> fullSyncServiceGrpcClient(
      @Named("GitSyncGrpcClientConfigs") Map<Microservice, GrpcClientConfig> clientConfigs) throws SSLException {
    Map<Microservice, FullSyncServiceBlockingStub> map = new HashMap<>();
    for (Map.Entry<Microservice, GrpcClientConfig> entry : clientConfigs.entrySet()) {
      map.put(entry.getKey(), FullSyncServiceGrpc.newBlockingStub(getChannel(entry.getValue())));
    }
    map.put(GmsClientConstants.moduleType,
        FullSyncServiceGrpc.newBlockingStub(
            InProcessChannelBuilder.forName(GitSyncGrpcConstants.GitSyncSdkInternalService).build()));
    return map;
  }
}
