/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync;

import static io.harness.Microservice.CF;
import static io.harness.Microservice.CORE;
import static io.harness.Microservice.PMS;
import static io.harness.Microservice.TEMPLATESERVICE;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.mockito.Mockito.mock;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.SCMGrpcClientModule;
import io.harness.ScmConnectionConfig;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.noop.NoOpProducer;
import io.harness.factory.ClosingFactory;
import io.harness.ff.FeatureFlagService;
import io.harness.ff.FeatureFlagServiceImpl;
import io.harness.gitsync.common.impl.GitBranchServiceImpl;
import io.harness.gitsync.common.impl.GitBranchSyncServiceImpl;
import io.harness.gitsync.common.impl.GitEntityServiceImpl;
import io.harness.gitsync.common.impl.GitSyncSettingsServiceImpl;
import io.harness.gitsync.common.impl.GitToHarnessProgressServiceImpl;
import io.harness.gitsync.common.impl.HarnessToGitHelperServiceImpl;
import io.harness.gitsync.common.impl.ScmOrchestratorServiceImpl;
import io.harness.gitsync.common.impl.UserSourceCodeManagerServiceImpl;
import io.harness.gitsync.common.impl.YamlGitConfigServiceImpl;
import io.harness.gitsync.common.impl.gittoharness.GitToHarnessProcessorServiceImpl;
import io.harness.gitsync.common.mappers.AzureRepoSCMMapper;
import io.harness.gitsync.common.mappers.GithubSCMMapper;
import io.harness.gitsync.common.mappers.GitlabSCMMapper;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.GitEntityService;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.GitToHarnessProgressService;
import io.harness.gitsync.common.service.HarnessToGitHelperService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.common.service.gittoharness.GitToHarnessProcessorService;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigService;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigServiceImpl;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityService;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityServiceImpl;
import io.harness.gitsync.core.fullsync.GitFullSyncProcessorService;
import io.harness.gitsync.core.fullsync.GitFullSyncProcessorServiceImpl;
import io.harness.gitsync.core.fullsync.impl.FullSyncJobServiceImpl;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.core.impl.GitCommitServiceImpl;
import io.harness.gitsync.core.impl.YamlChangeSetLifeCycleManagerServiceImpl;
import io.harness.gitsync.core.impl.YamlChangeSetServiceImpl;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.gitsync.core.service.YamlChangeSetLifeCycleManagerService;
import io.harness.gitsync.core.service.YamlChangeSetService;
import io.harness.gitsync.gitsyncerror.impl.GitSyncErrorServiceImpl;
import io.harness.gitsync.gitsyncerror.service.GitSyncErrorService;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.impl.scm.SCMServiceGitClientImpl;
import io.harness.lock.PersistentLocker;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.NGCoreModule;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.entitysetupusage.EntitySetupUsageModule;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.userprofile.services.api.SourceCodeManagerService;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueController;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.rule.InjectorRuleMixin;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.ManagerRegistrars;
import io.harness.service.ScmClient;
import io.harness.springdata.HTransactionTemplate;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.waiter.AbstractWaiterModule;
import io.harness.waiter.WaiterConfiguration;
import io.harness.waiter.WaiterConfiguration.PersistenceLayer;

import software.wings.service.impl.security.NGEncryptorService;
import software.wings.service.impl.security.NGEncryptorServiceImpl;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.DX)
@Slf4j
public class GitSyncTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  protected Injector injector;
  ClosingFactory closingFactory;

  public GitSyncTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(ConnectorService.class)
            .annotatedWith(Names.named(DEFAULT_CONNECTOR_SERVICE))
            .toInstance(mock(ConnectorService.class));
        bind(ConnectorService.class)
            .annotatedWith(Names.named("connectorDecoratorService"))
            .toInstance(mock(ConnectorService.class));
        bind(SecretManagerClientService.class).toInstance(mock(SecretManagerClientService.class));
        bind(new TypeLiteral<Supplier<DelegateCallbackToken>>() {
        }).toInstance(Suppliers.ofInstance(DelegateCallbackToken.newBuilder().build()));
        bind(DelegateServiceGrpcClient.class).toInstance(mock(DelegateServiceGrpcClient.class));
        bind(SecretCrudService.class).toInstance(mock(SecretCrudService.class));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.GIT_CONFIG_STREAM))
            .toInstance(mock(NoOpProducer.class));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.GIT_FULL_SYNC_STREAM))
            .toInstance(mock(NoOpProducer.class));
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.ENTITY_CRUD))
            .toInstance(mock(NoOpProducer.class));
        bind(ExecutorService.class).toInstance(mock(ExecutorService.class));
        bind(SourceCodeManagerService.class).toInstance(mock(SourceCodeManagerService.class));
        bind(PersistentLocker.class).toInstance(mock(PersistentLocker.class));
        bind(WebhookEventService.class).toInstance(mock(WebhookEventService.class));
        bind(AccountClient.class).toInstance(mock(AccountClient.class));
        bind(AccountClient.class).annotatedWith(Names.named("PRIVILEGED")).toInstance(mock(AccountClient.class));
        bind(NGEncryptorService.class).toInstance(mock(NGEncryptorServiceImpl.class));
        bind(FeatureFlagService.class).toInstance(mock(FeatureFlagServiceImpl.class));
        bind(YamlChangeSetService.class).toInstance(mock(YamlChangeSetServiceImpl.class));
        bind(YamlChangeSetLifeCycleManagerService.class)
            .toInstance(mock(YamlChangeSetLifeCycleManagerServiceImpl.class));
        bind(UserSourceCodeManagerService.class).toInstance(mock(UserSourceCodeManagerServiceImpl.class));
        bind(GitBranchService.class).toInstance(mock(GitBranchServiceImpl.class));
        bind(GitBranchSyncService.class).toInstance(mock(GitBranchSyncServiceImpl.class));
        bind(GitToHarnessProgressService.class).toInstance(mock(GitToHarnessProgressServiceImpl.class));
        bind(ScmOrchestratorService.class).toInstance(mock(ScmOrchestratorServiceImpl.class));
        bind(YamlGitConfigService.class).toInstance(mock(YamlGitConfigServiceImpl.class));
        bind(GitToHarnessProcessorService.class).toInstance(mock(GitToHarnessProcessorServiceImpl.class));
        bind(GitCommitService.class).toInstance(mock(GitCommitServiceImpl.class));
        bind(GitSyncErrorService.class).toInstance(mock(GitSyncErrorServiceImpl.class));
        bind(GitSyncSettingsService.class).toInstance(mock(GitSyncSettingsServiceImpl.class));
        bind(GitEntityService.class).toInstance(mock(GitEntityServiceImpl.class));
        bind(HarnessToGitHelperService.class).toInstance(mock(HarnessToGitHelperServiceImpl.class));
        bind(GitFullSyncEntityService.class).toInstance(mock(GitFullSyncEntityServiceImpl.class));
        bind(GitFullSyncProcessorService.class).toInstance(mock(GitFullSyncProcessorServiceImpl.class));
        bind(GitFullSyncConfigService.class).toInstance(mock(GitFullSyncConfigServiceImpl.class));
        bind(FullSyncJobService.class).toInstance(mock(FullSyncJobServiceImpl.class));
        bind(ScmClient.class).toInstance(mock(SCMServiceGitClientImpl.class));
        bind(HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub.class)
            .toInstance(mock(HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub.class));
        MapBinder<SCMType, UserSourceCodeManagerMapper> sourceCodeManagerMapBinder =
            MapBinder.newMapBinder(binder(), SCMType.class, UserSourceCodeManagerMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.GITHUB).to(GithubSCMMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.GITLAB).to(GitlabSCMMapper.class);
        sourceCodeManagerMapBinder.addBinding(SCMType.AZURE_REPO).to(AzureRepoSCMMapper.class);
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
        bind(Producer.class)
            .annotatedWith(Names.named(EventsFrameworkConstants.SETUP_USAGE))
            .toInstance(mock(NoOpProducer.class));
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(ManagerRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(ManagerRegistrars.springConverters)
            .build();
      }

      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }

      @Provides
      @Singleton
      Map<EntityType, Microservice> getEntityTypeMicroserviceMap() {
        return ImmutableMap.<EntityType, Microservice>builder()
            .put(EntityType.CONNECTORS, CORE)
            .put(EntityType.PIPELINES, PMS)
            .put(EntityType.FEATURE_FLAGS, CF)
            .put(EntityType.INPUT_SETS, PMS)
            .put(EntityType.TEMPLATE, TEMPLATESERVICE)
            .build();
      }

      @Provides
      @Singleton
      List<Microservice> getMicroservicesProcessingOrder() {
        return Arrays.asList(CORE, TEMPLATESERVICE, CF, PMS);
      }

      @Provides
      @Singleton
      public Map<Microservice, GitToHarnessServiceGrpc.GitToHarnessServiceBlockingStub> gitToHarnessServiceGrpcClient(
          @Named("GitSyncGrpcClientConfigs") Map<Microservice, GrpcClientConfig> clientConfigs) throws SSLException {
        Map<Microservice, GitToHarnessServiceGrpc.GitToHarnessServiceBlockingStub> map = new HashMap<>();
        return map;
      }

      @Provides
      @Named(OUTBOX_TRANSACTION_TEMPLATE)
      @Singleton
      TransactionTemplate getTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new HTransactionTemplate(mongoTransactionManager, false);
      }

      @Provides
      @Singleton
      OutboxService getOutboxService(OutboxEventRepository outboxEventRepository) {
        return new OutboxServiceImpl(new OutboxDaoImpl(outboxEventRepository), NG_DEFAULT_OBJECT_MAPPER);
      }

      @Provides
      @Singleton
      @Named("GitSyncGrpcClientConfigs")
      public Map<Microservice, GrpcClientConfig> grpcClientConfigs() {
        Map<Microservice, GrpcClientConfig> map = new HashMap<>();
        map.put(CORE, GrpcClientConfig.builder().target("localhost:12001").authority("localhost").build());
        return map;
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }
    });
    modules.add(KryoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
    modules.add(TestMongoModule.getInstance());
    modules.add(new SpringPersistenceTestModule());
    modules.add(NGCoreModule.getInstance());
    modules.add(new AbstractWaiterModule() {
      @Override
      public WaiterConfiguration waiterConfiguration() {
        return WaiterConfiguration.builder().persistenceLayer(PersistenceLayer.MORPHIA).build();
      }
    });
    modules.add(new EntitySetupUsageModule());
    modules.add(new SCMGrpcClientModule(ScmConnectionConfig.builder().url("dummyurl").build()));
    // modules.add(GitSyncModule.getInstance());
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
