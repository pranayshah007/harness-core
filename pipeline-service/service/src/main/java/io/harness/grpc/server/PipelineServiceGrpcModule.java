/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.server;

import io.harness.ModuleType;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptGrpcService;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc;
import io.harness.pms.contracts.plan.PlanCreationServiceGrpc.PlanCreationServiceBlockingStub;
import io.harness.pms.contracts.plan.PluginInfoProviderServiceGrpc;
import io.harness.pms.plan.execution.data.service.expressions.EngineExpressionGrpcServiceImpl;
import io.harness.pms.plan.execution.data.service.outcome.OutcomeServiceGrpcServerImpl;
import io.harness.pms.plan.execution.data.service.outputs.SweepingOutputServiceImpl;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.service.execution.PmsExecutionGrpcService;
import io.harness.pms.template.EntityReferenceGrpcService;
import io.harness.pms.template.VariablesServiceImpl;

import com.google.common.annotations.VisibleForTesting;
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
import io.grpc.ServerInterceptor;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.services.HealthStatusManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineServiceGrpcModule extends AbstractModule {
  private static PipelineServiceGrpcModule instance;
  private final String deployMode = System.getenv().get("DEPLOY_MODE");

  public static PipelineServiceGrpcModule getInstance() {
    if (instance == null) {
      instance = new PipelineServiceGrpcModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    Multibinder<ServerInterceptor> serverInterceptorMultibinder =
        Multibinder.newSetBinder(binder(), ServerInterceptor.class);
    serverInterceptorMultibinder.addBinding().to(PipelineServiceGrpcErrorHandler.class);

    Multibinder<Service> serviceBinder = Multibinder.newSetBinder(binder(), Service.class);
    serviceBinder.addBinding().to(Key.get(Service.class, Names.named("pms-grpc-service")));
    serviceBinder.addBinding().to(Key.get(Service.class, Names.named("pms-grpc-internal-service")));
  }

  @Provides
  @Singleton
  public ServiceManager serviceManager(Set<Service> services) {
    return new ServiceManager(services);
  }

  @Provides
  @Singleton
  public Map<ModuleType, PlanCreationServiceBlockingStub> grpcClients(PipelineServiceConfiguration configuration)
      throws SSLException {
    Map<ModuleType, PlanCreationServiceBlockingStub> map = new HashMap<>();
    map.put(ModuleType.PMS,
        PlanCreationServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      map.put(ModuleType.fromString(entry.getKey()),
          PlanCreationServiceGrpc.newBlockingStub(
              getChannel(entry.getValue(), configuration.getGrpcNegotiationType())));
    }
    return map;
  }

  @Provides
  @Singleton
  public Map<ModuleType, RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub> getExpressionExecutionClients(
      PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub> map = new HashMap<>();

    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      map.put(ModuleType.fromString(entry.getKey()),
          RemoteFunctorServiceGrpc.newBlockingStub(
              getChannel(entry.getValue(), configuration.getGrpcNegotiationType())));
    }

    return map;
  }

  @Provides
  @Singleton
  public Map<ModuleType, JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub> getJsonExpansionHandlerClients(
      PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub> map = new HashMap<>();

    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      map.put(ModuleType.fromString(entry.getKey()),
          JsonExpansionServiceGrpc.newBlockingStub(
              getChannel(entry.getValue(), configuration.getGrpcNegotiationType())));
    }
    map.put(ModuleType.PMS,
        JsonExpansionServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    return map;
  }

  @Provides
  @Singleton
  public Map<ModuleType, PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub>
  getPluginInfoProviderClients(PipelineServiceConfiguration configuration) throws SSLException {
    Map<ModuleType, PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub> map = new HashMap<>();
    for (Map.Entry<String, GrpcClientConfig> entry : configuration.getGrpcClientConfigs().entrySet()) {
      map.put(ModuleType.fromString(entry.getKey()),
          PluginInfoProviderServiceGrpc.newBlockingStub(
              getChannel(entry.getValue(), configuration.getGrpcNegotiationType())));
    }
    map.put(ModuleType.PMS,
        PluginInfoProviderServiceGrpc.newBlockingStub(InProcessChannelBuilder.forName("pmsSdkInternal").build()));
    return map;
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

  @Provides
  @Singleton
  @Named("pms-grpc-service")
  public Service pmsGrpcService(PipelineServiceConfiguration configuration, HealthStatusManager healthStatusManager,
      Set<BindableService> services, Set<ServerInterceptor> serverInterceptors) {
    return new GrpcServer(
        configuration.getGrpcServerConfig().getConnectors().get(0), services, serverInterceptors, healthStatusManager);
  }

  @Provides
  @Singleton
  @Named("pms-grpc-internal-service")
  public Service pmsGrpcInternalService(HealthStatusManager healthStatusManager, Set<BindableService> services,
      Set<ServerInterceptor> serverInterceptors) {
    return new GrpcInProcessServer(
        ModuleType.PMS.name().toLowerCase(), services, serverInterceptors, healthStatusManager);
  }

  @Provides
  @VisibleForTesting
  Set<BindableService> bindableServices(HealthStatusManager healthStatusManager,
      PmsSdkInstanceService pmsSdkInstanceService, PmsExecutionGrpcService pmsExecutionGrpcService,
      SweepingOutputServiceImpl sweepingOutputService, OutcomeServiceGrpcServerImpl outcomeServiceGrpcServer,
      EngineExpressionGrpcServiceImpl engineExpressionGrpcService, InterruptGrpcService interruptGrpcService,
      EntityReferenceGrpcService entityReferenceService, VariablesServiceImpl variablesService) {
    Set<BindableService> services = new HashSet<>();
    services.add(healthStatusManager.getHealthService());
    services.add(pmsSdkInstanceService);
    services.add(pmsExecutionGrpcService);
    services.add(sweepingOutputService);
    services.add(outcomeServiceGrpcServer);
    services.add(engineExpressionGrpcService);
    services.add(interruptGrpcService);
    services.add(entityReferenceService);
    services.add(variablesService);
    return services;
  }

  @VisibleForTesting
  boolean shouldUsePlainTextNegotiationType(NegotiationType negotiationType, String deployMode) {
    return "ONPREM".equals(deployMode) || "KUBERNETES_ONPREM".equals(deployMode)
        || (negotiationType != null && negotiationType.equals(NegotiationType.PLAINTEXT));
  }
}
