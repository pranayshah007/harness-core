/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core;

import io.harness.ModuleType;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcInProcessServer;
import io.harness.grpc.server.GrpcServer;
import io.harness.pms.contracts.plan.PmsServiceGrpc;
import io.harness.pms.contracts.plan.PmsServiceGrpc.PmsServiceBlockingStub;
import io.harness.pms.contracts.service.EngineExpressionProtoServiceGrpc;
import io.harness.pms.contracts.service.EngineExpressionProtoServiceGrpc.EngineExpressionProtoServiceBlockingStub;
import io.harness.pms.contracts.service.InterruptProtoServiceGrpc;
import io.harness.pms.contracts.service.InterruptProtoServiceGrpc.InterruptProtoServiceBlockingStub;
import io.harness.pms.contracts.service.OutcomeProtoServiceGrpc;
import io.harness.pms.contracts.service.OutcomeProtoServiceGrpc.OutcomeProtoServiceBlockingStub;
import io.harness.pms.contracts.service.PmsExecutionServiceGrpc;
import io.harness.pms.contracts.service.PmsExecutionServiceGrpc.PmsExecutionServiceBlockingStub;
import io.harness.pms.contracts.service.SweepingOutputServiceGrpc;
import io.harness.pms.contracts.service.SweepingOutputServiceGrpc.SweepingOutputServiceBlockingStub;
import io.harness.pms.sdk.core.execution.expression.RemoteFunctorService;
import io.harness.pms.sdk.core.governance.JsonExpansionService;
import io.harness.pms.sdk.core.plan.creation.creators.PlanCreatorService;
import io.harness.pms.sdk.core.plugin.PluginInfoProviderService;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.grpc.BindableService;
import io.grpc.Channel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.services.HealthStatusManager;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PmsSdkGrpcModule extends AbstractModule {
  private final PmsSdkCoreConfig config;
  private static PmsSdkGrpcModule instance;
  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  public static PmsSdkGrpcModule getInstance(PmsSdkCoreConfig config) {
    if (instance == null) {
      instance = new PmsSdkGrpcModule(config);
    }
    return instance;
  }

  private PmsSdkGrpcModule(PmsSdkCoreConfig config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    Multibinder<Service> serviceBinder = Multibinder.newSetBinder(binder(), Service.class, Names.named("pmsServices"));
    serviceBinder.addBinding().to(Key.get(Service.class, Names.named("pms-sdk-grpc-service")));
  }

  @Provides
  @Singleton
  @Named("pms-sdk-grpc-service")
  public Service pmsSdkGrpcService(HealthStatusManager healthStatusManager, PlanCreatorService planCreatorService,
      RemoteFunctorService remoteFunctorService, JsonExpansionService jsonExpansionService,
      PluginInfoProviderService pluginInfoService) {
    Set<BindableService> sdkServices = new HashSet<>();
    sdkServices.add(healthStatusManager.getHealthService());
    sdkServices.add(planCreatorService);
    sdkServices.add(remoteFunctorService);
    sdkServices.add(jsonExpansionService);
    sdkServices.add(pluginInfoService);
    if (config.getSdkDeployMode() == SdkDeployMode.REMOTE_IN_PROCESS) {
      return new GrpcInProcessServer("pmsSdkInternal", sdkServices, Collections.emptySet(), healthStatusManager);
    }
    return new GrpcServer(
        config.getGrpcServerConfig().getConnectors().get(0), sdkServices, Collections.emptySet(), healthStatusManager);
  }

  private Channel getChannel() throws SSLException {
    if (config.getSdkDeployMode() == SdkDeployMode.REMOTE_IN_PROCESS) {
      return InProcessChannelBuilder.forName(ModuleType.PMS.name().toLowerCase()).build();
    }

    GrpcClientConfig clientConfig = config.getGrpcClientConfig();
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
  public PmsServiceBlockingStub pmsGrpcClient() throws SSLException {
    return PmsServiceGrpc.newBlockingStub(getChannel());
  }

  @Provides
  @Singleton
  public SweepingOutputServiceBlockingStub sweepingOutputGrpcClient() throws SSLException {
    return SweepingOutputServiceGrpc.newBlockingStub(getChannel());
  }

  @Provides
  @Singleton
  public InterruptProtoServiceBlockingStub interruptProtoGrpcClient() throws SSLException {
    return InterruptProtoServiceGrpc.newBlockingStub(getChannel());
  }

  @Provides
  @Singleton
  public OutcomeProtoServiceBlockingStub outcomeGrpcClient() throws SSLException {
    return OutcomeProtoServiceGrpc.newBlockingStub(getChannel());
  }

  @Provides
  @Singleton
  public PmsExecutionServiceBlockingStub executionServiceGrpcClient() throws SSLException {
    return PmsExecutionServiceGrpc.newBlockingStub(getChannel());
  }

  @Provides
  @Singleton
  public EngineExpressionProtoServiceBlockingStub engineExpressionGrpcClient() throws SSLException {
    return EngineExpressionProtoServiceGrpc.newBlockingStub(getChannel());
  }

  @Provides
  @Singleton
  @Named("pmsSDKServiceManager")
  public ServiceManager serviceManager(@Named("pmsServices") Set<Service> services) {
    return new ServiceManager(services);
  }
}
