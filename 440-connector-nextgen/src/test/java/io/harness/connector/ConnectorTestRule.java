/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.ConnectorConstants.CONNECTOR_DECORATOR_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;

import static org.mockito.Mockito.mock;

import io.harness.AccessControlClientConfiguration;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.client.NgConnectorManagerClient;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.DecryptionHelperViaManager;
import io.harness.connector.impl.ConnectorActivityServiceImpl;
import io.harness.connector.services.ConnectorActivityService;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.factory.ClosingFactory;
import io.harness.favorites.services.FavoritesService;
import io.harness.ff.FeatureFlagService;
import io.harness.gitsync.clients.YamlGitConfigClient;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.persistance.testing.GitSyncablePersistenceTestModule;
import io.harness.gitsync.persistance.testing.NoOpGitAwarePersistenceImpl;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.validator.service.api.NGHostValidationService;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.remote.CEAzureSetupConfig;
import io.harness.remote.CEGcpSetupConfig;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.InjectorRuleMixin;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.ConnectorNextGenRegistrars;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.PersistenceRegistrars;
import io.harness.springdata.HTransactionTemplate;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.yaml.YamlSdkModule;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import software.wings.service.impl.security.NGEncryptorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.jackson.Jackson;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(DX)
public class ConnectorTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;

  public ConnectorTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(TimeLimiter.class).toInstance(HTimeLimiter.create());
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(ConnectorActivityService.class).to(ConnectorActivityServiceImpl.class);
        bind(ProjectService.class).toInstance(mock(ProjectService.class));
        bind(ConnectorService.class)
            .annotatedWith(Names.named(CONNECTOR_DECORATOR_SERVICE))
            .toInstance(mock(ConnectorService.class));
        bind(NGEncryptorService.class).toInstance(mock(NGEncryptorService.class));
        bind(OrganizationService.class).toInstance(mock(OrganizationService.class));
        bind(NGActivityService.class).toInstance(mock(NGActivityService.class));
        bind(SecretManagerClientService.class).toInstance(mock(SecretManagerClientService.class));
        bind(SecretManagerClientService.class)
            .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
            .toInstance(mock(SecretManagerClientService.class));
        bind(DecryptionHelper.class).toInstance(mock(DecryptionHelperViaManager.class));
        bind(SecretNGManagerClient.class).toInstance(mock(SecretNGManagerClient.class));
        bind(DelegateServiceGrpcClient.class).toInstance(mock(DelegateServiceGrpcClient.class));
        bind(SecretCrudService.class).toInstance(mock(SecretCrudService.class));
        bind(NGSecretManagerService.class).toInstance(mock(NGSecretManagerService.class));
        bind(AccessControlClientConfiguration.class).toInstance(mock(AccessControlClientConfiguration.class));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_ACTIVITY))
            .toInstance(mock(NoOpProducer.class));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
            .toInstance(mock(NoOpProducer.class));
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(GitAwarePersistence.class).to(NoOpGitAwarePersistenceImpl.class);
        bind(GitSyncSdkService.class).toInstance(mock(GitSyncSdkService.class));
        bind(YamlGitConfigClient.class).toInstance(mock(YamlGitConfigClient.class));
        bind(NGHostValidationService.class).toInstance(mock(NGHostValidationService.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagService.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
        bind(NGSettingsClient.class).toInstance(mock(NGSettingsClient.class));
        bind(EntitySetupUsageService.class).toInstance(mock(EntitySetupUsageService.class));
        bind(FavoritesService.class).toInstance(mock(FavoritesService.class));
        bind(NgConnectorManagerClient.class).toInstance(mock(NgConnectorManagerClient.class));
      }
    });
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new GitSyncablePersistenceTestModule());
    modules.add(ConnectorModule.getInstance(
        io.harness.remote.NextGenConfig.builder().ceNextGenServiceSecret("test_secret").build(),
        ServiceHttpClientConfig.builder().baseUrl("http://localhost:7457/").build()));
    modules.add(KryoModule.getInstance());
    modules.add(YamlSdkModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      OutboxService registerOutboxService() {
        return mock(OutboxService.class);
      }

      @Provides
      @Singleton
      @Named(OUTBOX_TRANSACTION_TEMPLATE)
      TransactionTemplate registerTransactionTemplate() {
        return mock(TransactionTemplate.class);
      }

      @Provides
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(ConnectorNextGenRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(ConnectorNextGenRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(PersistenceRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<YamlSchemaRootClass> yamlSchemaRootClass() {
        return ImmutableList.<YamlSchemaRootClass>builder()
            .addAll(ConnectorNextGenRegistrars.yamlSchemaRegistrars)
            .build();
      }

      @Provides
      @Named("yaml-schema-mapper")
      @Singleton
      public ObjectMapper getYamlSchemaObjectMapper() {
        return Jackson.newObjectMapper();
      }

      @Provides
      @Singleton
      CEAwsSetupConfig ceAwsSetupConfig() {
        return CEAwsSetupConfig.builder().build();
      }

      @Provides
      @Singleton
      CEAzureSetupConfig ceAzureSetupConfig() {
        return CEAzureSetupConfig.builder().build();
      }

      @Provides
      @Singleton
      CEGcpSetupConfig ceGcpSetupConfig() {
        return CEGcpSetupConfig.builder().build();
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }

      @Provides
      @Singleton
      TemplateResourceClient getTemplateResourceClient() {
        return mock(TemplateResourceClient.class);
      }
    });
    return modules;
  }

  @Override
  public void initialize(Injector injector, List<Module> modules) {
    for (Module module : modules) {
      if (module instanceof ServersModule) {
        for (Closeable server : ((ServersModule) module).servers(injector)) {
          closingFactory.addServer(server);
        }
      }
    }
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return applyInjector(log, base, method, target);
  }
}
