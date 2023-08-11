/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.notification;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.NOTIFICATION_SERVICE;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.NOTIFICATION_ENTITY;
import static io.harness.notification.NotificationServiceConstants.MAILSERVICE;
import static io.harness.notification.NotificationServiceConstants.MSTEAMSSERVICE;
import static io.harness.notification.NotificationServiceConstants.PAGERDUTYSERVICE;
import static io.harness.notification.NotificationServiceConstants.SLACKSERVICE;
import static io.harness.notification.NotificationServiceConstants.WEBHOOKSERVICE;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
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
import io.harness.mongo.queue.NGMongoQueueConsumer;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.event.MessageListener;
import io.harness.notification.SmtpConfig;
import io.harness.notification.entities.MongoNotificationRequest;
import io.harness.notification.eventbackbone.MessageConsumer;
import io.harness.notification.eventbackbone.MongoMessageConsumer;
import io.harness.notification.eventframework.NotificationEntityCrudStreamListener;
import io.harness.notification.modules.SmtpConfigClientModule;
import io.harness.notification.service.ChannelServiceImpl;
import io.harness.notification.service.MSTeamsServiceImpl;
import io.harness.notification.service.MailServiceImpl;
import io.harness.notification.service.NotificationServiceImpl;
import io.harness.notification.service.NotificationSettingsServiceImpl;
import io.harness.notification.service.NotificationTemplateServiceImpl;
import io.harness.notification.service.PagerDutyServiceImpl;
import io.harness.notification.service.SeedDataPopulaterServiceImpl;
import io.harness.notification.service.SlackServiceImpl;
import io.harness.notification.service.WebhookServiceImpl;
import io.harness.notification.service.api.ChannelService;
import io.harness.notification.service.api.NotificationService;
import io.harness.notification.service.api.NotificationSettingsService;
import io.harness.notification.service.api.NotificationTemplateService;
import io.harness.notification.service.api.SeedDataPopulaterService;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.platform.PlatformConfiguration;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueController;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.NotificationRegistrars;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.threading.ExecutorModule;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;
import io.harness.usergroups.UserGroupClientModule;
import io.harness.userng.UserNGClientModule;
import io.harness.version.VersionModule;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.parameternameprovider.ReflectionParameterNameProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.MongoTemplate;
import ru.vyarus.guice.validator.ValidationModule;

@Slf4j
@OwnedBy(PL)
public class NotificationServiceModule extends AbstractModule {
  PlatformConfiguration appConfig;

  public NotificationServiceModule(PlatformConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return Suppliers.memoize(() -> getDelegateCallbackToken(delegateServiceGrpcClient, appConfig));
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, PlatformConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("ns")
                                  .setConnection(appConfig.getNotificationServiceConfig().getMongoConfig().getUri())
                                  .build())
            .build());
    log.info("delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Override
  public void configure() {
    install(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(NotificationRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(NotificationRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return appConfig.getNotificationServiceConfig().getMongoConfig();
      }
    });

    install(ExecutorModule.getInstance());
    install(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(PersistenceLayer.MORPHIA).build();
      }
    });
    bind(ManagedScheduledExecutorService.class)
        .annotatedWith(Names.named("delegate-response"))
        .toInstance(new ManagedScheduledExecutorService("delegate-response"));
    bind(PlatformConfiguration.class).toInstance(appConfig);
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    bind(HPersistence.class).to(MongoPersistence.class);
    install(DelegateServiceDriverModule.getInstance(false, false));
    install(new DelegateServiceDriverGrpcClientModule(appConfig.getPlatformSecrets().getNgManagerServiceSecret(),
        this.appConfig.getNotificationServiceConfig().getDelegateServiceGrpcConfig().getTarget(),
        this.appConfig.getNotificationServiceConfig().getDelegateServiceGrpcConfig().getAuthority(), true));

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

    install(new NotificationPersistenceModule());

    install(new UserGroupClientModule(appConfig.getRbacServiceConfig(),
        appConfig.getPlatformSecrets().getNgManagerServiceSecret(), NOTIFICATION_SERVICE.getServiceId()));
    install(new UserClientModule(appConfig.getManagerServiceConfig(),
        appConfig.getPlatformSecrets().getNgManagerServiceSecret(), NOTIFICATION_SERVICE.getServiceId()));
    install(new UserNGClientModule(appConfig.getNgManagerServiceConfig(),
        appConfig.getPlatformSecrets().getNgManagerServiceSecret(), NOTIFICATION_SERVICE.getServiceId()));
    bind(ChannelService.class).to(ChannelServiceImpl.class);
    install(new SmtpConfigClientModule(
        appConfig.getManagerServiceConfig(), appConfig.getPlatformSecrets().getNgManagerServiceSecret()));
    bind(NotificationSettingsService.class).to(NotificationSettingsServiceImpl.class);
    bind(SeedDataPopulaterService.class).to(SeedDataPopulaterServiceImpl.class);
    bind(ChannelService.class).annotatedWith(Names.named(MAILSERVICE)).to(MailServiceImpl.class);
    bind(ChannelService.class).annotatedWith(Names.named(SLACKSERVICE)).to(SlackServiceImpl.class);
    bind(ChannelService.class).annotatedWith(Names.named(PAGERDUTYSERVICE)).to(PagerDutyServiceImpl.class);
    bind(ChannelService.class).annotatedWith(Names.named(MSTEAMSSERVICE)).to(MSTeamsServiceImpl.class);
    bind(ChannelService.class).annotatedWith(Names.named(WEBHOOKSERVICE)).to(WebhookServiceImpl.class);
    bind(NotificationService.class).to(NotificationServiceImpl.class);
    bind(NotificationTemplateService.class).to(NotificationTemplateServiceImpl.class);
    bindMessageConsumer();
    install(new TokenClientModule(this.appConfig.getRbacServiceConfig(),
        this.appConfig.getPlatformSecrets().getNgManagerServiceSecret(), NOTIFICATION_SERVICE.getServiceId()));
    install(new EventsFrameworkModule(this.appConfig.getEventsFrameworkConfiguration()));
    registerEventListeners();
  }

  private void registerEventListeners() {
    bind(MessageListener.class)
        .annotatedWith(Names.named(NOTIFICATION_ENTITY + ENTITY_CRUD))
        .to(NotificationEntityCrudStreamListener.class);
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return ImmutableMap.<Class, String>builder()
        .put(DelegateSyncTaskResponse.class, "ns_delegateSyncTaskResponses")
        .put(DelegateAsyncTaskResponse.class, "ns_delegateAsyncTaskResponses")
        .put(DelegateTaskProgressResponse.class, "ns_delegateTaskProgressResponses")
        .build();
  }

  private ValidatorFactory getValidatorFactory() {
    return Validation.byDefaultProvider()
        .configure()
        .parameterNameProvider(new ReflectionParameterNameProvider())
        .buildValidatorFactory();
  }

  private void bindMessageConsumer() {
    log.info("Using Mongo as the message broker");
    bind(MessageConsumer.class).to(MongoMessageConsumer.class);
  }

  @Provides
  @Singleton
  SmtpConfig getSmtpConfig() {
    return appConfig.getNotificationServiceConfig().getSmtpConfig();
  }

  @Provides
  @Singleton
  QueueConsumer<MongoNotificationRequest> getQueueConsumer(MongoTemplate mongoTemplate) {
    return new NGMongoQueueConsumer<>(MongoNotificationRequest.class, ofSeconds(5), new ArrayList<>(), mongoTemplate);
  }
}
