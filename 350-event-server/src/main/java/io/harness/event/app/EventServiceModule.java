/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.app;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.CfClientModule;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.delegate.authenticator.DelegateTokenAuthenticatorImpl;
import io.harness.event.MessageProcessorType;
import io.harness.event.grpc.EventPublisherServerImpl;
import io.harness.event.grpc.MessageProcessor;
import io.harness.event.metrics.EventServiceMetricsPublisher;
import io.harness.event.service.impl.EventDataBulkWriteServiceImpl;
import io.harness.event.service.impl.EventPublisherServiceImpl;
import io.harness.event.service.impl.LastReceivedPublishedMessageRepositoryImpl;
import io.harness.event.service.intfc.EventDataBulkWriteService;
import io.harness.event.service.intfc.EventPublisherService;
import io.harness.event.service.intfc.LastReceivedPublishedMessageRepository;
import io.harness.ff.FeatureFlagService;
import io.harness.ff.FeatureFlagServiceImpl;
import io.harness.grpc.auth.DelegateAuthServerInterceptor;
import io.harness.grpc.exception.GrpcExceptionMapper;
import io.harness.grpc.exception.WingsExceptionGrpcMapper;
import io.harness.grpc.server.GrpcServerExceptionHandler;
import io.harness.grpc.server.GrpcServerModule;
import io.harness.lock.PersistentLocker;
import io.harness.lock.noop.PersistentNoopLocker;
import io.harness.metrics.impl.DelegateMetricsServiceImpl;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.metrics.modules.MetricsModule;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.persistence.HPersistence;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.service.impl.DelegateSecretManagerImpl;
import io.harness.service.impl.agent.mtls.AgentMtlsEndpointServiceReadOnlyImpl;
import io.harness.service.intfc.AgentMtlsEndpointService;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;

import software.wings.dl.WingsMongoPersistence;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.security.NoOpSecretManagerImpl;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;

@OwnedBy(PL)
public class EventServiceModule extends AbstractModule {
  private final EventServiceConfig eventServiceConfig;
  private static final int OPEN_CENSUS_EXPORT_INTERVAL_MINUTES = 5;

  public EventServiceModule(EventServiceConfig eventServiceConfig) {
    this.eventServiceConfig = eventServiceConfig;
  }

  @Override
  protected void configure() {
    bind(EventServiceConfig.class).toInstance(eventServiceConfig);
    bind(HPersistence.class).to(WingsMongoPersistence.class).in(Singleton.class);
    bind(WingsPersistence.class).to(WingsMongoPersistence.class).in(Singleton.class);

    // event service only needs reading capabilities for datapath authority validation
    bind(AgentMtlsEndpointService.class).to(AgentMtlsEndpointServiceReadOnlyImpl.class);
    bind(DelegateTokenAuthenticator.class).to(DelegateTokenAuthenticatorImpl.class).in(Singleton.class);
    bind(DelegateMetricsService.class).to(DelegateMetricsServiceImpl.class);

    bind(SecretManager.class).to(NoOpSecretManagerImpl.class);
    bind(EncryptedSettingAttributes.class).to(NoOpSecretManagerImpl.class);
    bind(LastReceivedPublishedMessageRepository.class).to(LastReceivedPublishedMessageRepositoryImpl.class);
    bind(EventDataBulkWriteService.class).to(EventDataBulkWriteServiceImpl.class);
    bind(EventPublisherService.class).to(EventPublisherServiceImpl.class);

    Multibinder<BindableService> bindableServiceMultibinder = Multibinder.newSetBinder(binder(), BindableService.class);
    bindableServiceMultibinder.addBinding().to(EventPublisherServerImpl.class);

    Multibinder<ServerInterceptor> serverInterceptorMultibinder =
        Multibinder.newSetBinder(binder(), ServerInterceptor.class);
    serverInterceptorMultibinder.addBinding().to(DelegateAuthServerInterceptor.class);

    Multibinder<GrpcExceptionMapper> expectionMapperMultibinder =
        Multibinder.newSetBinder(binder(), GrpcExceptionMapper.class);
    expectionMapperMultibinder.addBinding().to(WingsExceptionGrpcMapper.class);

    Provider<Set<GrpcExceptionMapper>> grpcExceptionMappersProvider =
        getProvider(Key.get(new TypeLiteral<Set<GrpcExceptionMapper>>() {}));
    serverInterceptorMultibinder.addBinding().toProvider(
        () -> new GrpcServerExceptionHandler(grpcExceptionMappersProvider));

    MapBinder<MessageProcessorType, MessageProcessor> mapBinder =
        MapBinder.newMapBinder(binder(), MessageProcessorType.class, MessageProcessor.class);
    for (MessageProcessorType messageProcessorType : MessageProcessorType.values()) {
      mapBinder.addBinding(messageProcessorType)
          .to(messageProcessorType.getMessageProcessorClass())
          .in(Singleton.class);
    }

    install(new GrpcServerModule(eventServiceConfig.getConnectors(), //
        getProvider(Key.get(new TypeLiteral<Set<BindableService>>() {})),
        getProvider(Key.get(new TypeLiteral<Set<ServerInterceptor>>() {}))));

    install(new RegistrarsModule());

    install(new MetricsModule(OPEN_CENSUS_EXPORT_INTERVAL_MINUTES));
    install(ExecutorModule.getInstance());
    bind(MetricsPublisher.class).to(EventServiceMetricsPublisher.class).in(Scopes.SINGLETON);

    bindCFServices();
    bind(DelegateSecretManager.class).to(DelegateSecretManagerImpl.class);
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  public Set<Class<?>> morphiaClasses() {
    return Collections.emptySet();
  }

  @Provides
  @Singleton
  public ServiceManager serviceManager(Set<Service> services) {
    return new ServiceManager(services);
  }

  /**
   * This dependency only exists for the CFMigrationService which EventService will never use. However,
   * since it is sharing the same module, we have to provide an implementation for the same. Hence, we are using
   * NOOP over here.
   */
  private void bindCFServices() {
    ExecutorModule.getInstance().setExecutorService(Executors.newCachedThreadPool());
    install(ExecutorModule.getInstance());
    install(TimeModule.getInstance());
    install(CfClientModule.getInstance());

    bind(PersistentLocker.class).to(PersistentNoopLocker.class).in(Scopes.SINGLETON);
    OptionalBinder.newOptionalBinder(binder(), AccountClient.class);
    bind(FeatureFlagService.class).to(FeatureFlagServiceImpl.class);
  }
}
