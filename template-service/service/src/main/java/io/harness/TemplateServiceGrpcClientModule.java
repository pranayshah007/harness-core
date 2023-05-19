/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcInProcessServer;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc.EntityReferenceServiceBlockingStub;
import io.harness.pms.contracts.service.VariablesServiceGrpc;
import io.harness.pms.contracts.service.VariablesServiceGrpc.VariablesServiceBlockingStub;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.grpc.Channel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import javax.net.ssl.SSLException;

public class TemplateServiceGrpcClientModule extends AbstractModule {
  private final TemplateServiceConfiguration configuration;
  private static TemplateServiceGrpcClientModule instance;
  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  public static TemplateServiceGrpcClientModule getInstance(TemplateServiceConfiguration configuration) {
    if (instance == null) {
      instance = new TemplateServiceGrpcClientModule(configuration);
    }
    return instance;
  }

  public TemplateServiceGrpcClientModule(TemplateServiceConfiguration configuration) {
    this.configuration = configuration;
  }

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
  public EntityReferenceServiceBlockingStub entityReferenceServiceClient() throws SSLException {
    return EntityReferenceServiceGrpc.newBlockingStub(getChannel(configuration.getPmsGrpcClientConfig()));
  }

  @Provides
  @Singleton
  public VariablesServiceBlockingStub variablesServiceClient() throws SSLException {
    return VariablesServiceGrpc.newBlockingStub(getChannel(configuration.getPmsGrpcClientConfig()));
  }
}
