/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.config;

import static io.harness.authorization.AuthorizationServiceHeader.AUDIT_EVENT_STREAMING;

import io.harness.audit.client.remote.streaming.StreamingDestinationClientModule;
import io.harness.auditevent.streaming.serializer.AuditEventStreamingRegistrar;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskProgressResponse;
import io.harness.govern.ProviderModule;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.queue.QueueController;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuditEventStreamingModule extends AbstractModule {
  AuditEventStreamingConfig auditEventStreamingConfig;

  public AuditEventStreamingModule(AuditEventStreamingConfig auditEventStreamingConfig) {
    this.auditEventStreamingConfig = auditEventStreamingConfig;
  }

  @Override
  protected void configure() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(AuditEventStreamingRegistrar.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(AuditEventStreamingRegistrar.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return auditEventStreamingConfig.getAuditEventDbConfig();
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return List.of(auditEventStreamingConfig.getAuditEventDbConfig().getAliasDBName());
      }
    });

    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));
    bind(QueueController.class).toInstance(new QueueController() {
      @Override
      public boolean isPrimary() {
        return true;
      }

      @Override
      public boolean isNotPrimary() {
        return false;
      }
    });
    registerDelegateTaskService();
    install(new StreamingDestinationClientModule(auditEventStreamingConfig.getAuditClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getPlatformServiceSecret(),
        AUDIT_EVENT_STREAMING.getServiceId()));
    install(new ConnectorResourceClientModule(auditEventStreamingConfig.getNgManagerClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getNgManagerServiceSecret(), AUDIT_EVENT_STREAMING.getServiceId(),
        ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(auditEventStreamingConfig.getNgManagerClientConfig(),
        auditEventStreamingConfig.getServiceSecrets().getNgManagerServiceSecret(),
        AUDIT_EVENT_STREAMING.getServiceId()));
    install(new AuditEventBatchPersistenceModule());
  }

  private void registerDelegateTaskService() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
          DelegateServiceGrpcClient delegateServiceGrpcClient) {
        return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient));
      }

      @Provides
      @Singleton
      @Named("morphiaClasses")
      Map<Class, String> morphiaCustomCollectionNames() {
        return ImmutableMap.<Class, String>builder()
            .put(DelegateSyncTaskResponse.class, "as_delegateSyncTaskResponses")
            .put(DelegateAsyncTaskResponse.class, "as_delegateAsyncTaskResponses")
            .put(DelegateTaskProgressResponse.class, "as_delegateTaskProgressResponses")
            .build();
      }
    });

    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(WaiterConfiguration.PersistenceLayer.SPRING).build();
      }
    });

    install(DelegateServiceDriverModule.getInstance(true, false));
    install(new DelegateServiceDriverGrpcClientModule(
        auditEventStreamingConfig.getServiceSecrets().getNgManagerServiceSecret(),
        auditEventStreamingConfig.getDelegateServiceGrpcConfig().getTarget(),
        auditEventStreamingConfig.getDelegateServiceGrpcConfig().getAuthority(), true));
  }

  private DelegateCallbackToken getDelegateCallbackToken(DelegateServiceGrpcClient delegateServiceClient) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("as")
                                  .setConnection(auditEventStreamingConfig.getAuditEventDbConfig().getUri())
                                  .build())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }
}
