/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.AUDIT_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.outbox.OutboxSDKConstants.DEFAULT_OUTBOX_POLL_CONFIGURATION;

import io.harness.AccessControlClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.AuditFilterModule;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditSettingsService;
import io.harness.audit.api.AuditYamlService;
import io.harness.audit.api.impl.AuditServiceImpl;
import io.harness.audit.api.impl.AuditSettingsServiceImpl;
import io.harness.audit.api.impl.AuditYamlServiceImpl;
import io.harness.audit.api.streaming.AggregateStreamingService;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.api.streaming.impl.AggregateStreamingServiceImpl;
import io.harness.audit.api.streaming.impl.StreamingServiceImpl;
import io.harness.audit.client.remote.AuditClientModule;
import io.harness.audit.eventframework.AccountEntityCrudStreamListener;
import io.harness.audit.repositories.streaming.StreamingBatchRepository;
import io.harness.audit.repositories.streaming.StreamingBatchRepositoryImpl;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.govern.ProviderModule;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.outbox.TransactionOutboxModule;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.platform.PlatformConfiguration;
import io.harness.queue.QueueController;
import io.harness.remote.client.ClientMode;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NGAuditServiceRegistrars;
import io.harness.springdata.HTransactionTemplate;
import io.harness.threading.ExecutorModule;
import io.harness.token.TokenClientModule;
import io.harness.version.VersionModule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.Map;
import java.util.Set;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(PL)
public class AuditServiceModule extends AbstractModule {
  PlatformConfiguration appConfig;

  public AuditServiceModule(PlatformConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(NGAuditServiceRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(NGAuditServiceRegistrars.morphiaRegistrars)
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
        return appConfig.getAuditServiceConfig().getMongoConfig();
      }
    });

    install(ExecutorModule.getInstance());
    bind(PlatformConfiguration.class).toInstance(appConfig);
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    bind(HPersistence.class).to(MongoPersistence.class);

    install(VersionModule.getInstance());
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
    install(new ValidationModule(getValidatorFactory()));

    install(new AuditPersistenceModule());

    install(new AuditFilterModule());
    install(new AuditClientModule(this.appConfig.getAuditServiceConfig().getAuditClientConfig(),
        this.appConfig.getPlatformSecrets().getNgManagerServiceSecret(), AUDIT_SERVICE.getServiceId(),
        this.appConfig.getAuditServiceConfig().isEnableAudit()));
    install(new TransactionOutboxModule(DEFAULT_OUTBOX_POLL_CONFIGURATION, AUDIT_SERVICE.getServiceId(),
        appConfig.getAuditServiceConfig().isExportMetricsToStackDriver()));

    bind(AuditYamlService.class).to(AuditYamlServiceImpl.class);
    bind(AuditService.class).to(AuditServiceImpl.class);
    bind(AuditSettingsService.class).to(AuditSettingsServiceImpl.class);
    bind(StreamingService.class).to(StreamingServiceImpl.class);
    bind(AggregateStreamingService.class).to(AggregateStreamingServiceImpl.class);
    bind(StreamingBatchRepository.class).to(StreamingBatchRepositoryImpl.class);
    install(
        AccessControlClientModule.getInstance(appConfig.getAccessControlClientConfig(), AUDIT_SERVICE.getServiceId()));
    install(new ConnectorResourceClientModule(this.appConfig.getNgManagerServiceConfig(),
        this.appConfig.getPlatformSecrets().getNgManagerServiceSecret(), AUDIT_SERVICE.toString(),
        ClientMode.PRIVILEGED));
    install(new TokenClientModule(this.appConfig.getManagerServiceConfig(),
        this.appConfig.getPlatformSecrets().getNgManagerServiceSecret(), AUDIT_SERVICE.getServiceId()));
    install(new EventsFrameworkModule(this.appConfig.getEventsFrameworkConfiguration()));
    registerEventListeners();
  }

  private void registerEventListeners() {
    bind(MessageListener.class)
        .annotatedWith(Names.named(ACCOUNT_ENTITY + ENTITY_CRUD))
        .to(AccountEntityCrudStreamListener.class);
  }

  @Provides
  @Singleton
  protected TransactionTemplate getTransactionTemplate(
      MongoTransactionManager mongoTransactionManager, MongoConfig mongoConfig) {
    return new HTransactionTemplate(mongoTransactionManager, mongoConfig.isTransactionsEnabled());
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder().build();
  }

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }
}
