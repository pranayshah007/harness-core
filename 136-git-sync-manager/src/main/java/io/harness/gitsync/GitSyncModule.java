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
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_FULL_SYNC_STREAM;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.GIT_COMMIT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.GIT_TO_HARNESS_PROGRESS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.YAML_CHANGE_SET;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.SCMJavaClientModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.PrimaryVersionManagerModule;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.gitsync.caching.service.GitFileCacheService;
import io.harness.gitsync.caching.service.GitFileCacheServiceImpl;
import io.harness.gitsync.client.GitSyncSdkGrpcClientModule;
import io.harness.gitsync.common.events.FullSyncMessageListener;
import io.harness.gitsync.common.impl.FullSyncTriggerServiceImpl;
import io.harness.gitsync.common.impl.GitBranchServiceImpl;
import io.harness.gitsync.common.impl.GitBranchSyncServiceImpl;
import io.harness.gitsync.common.impl.GitEntityServiceImpl;
import io.harness.gitsync.common.impl.GitSyncSettingsServiceImpl;
import io.harness.gitsync.common.impl.GitToHarnessProgressServiceImpl;
import io.harness.gitsync.common.impl.HarnessToGitHelperServiceImpl;
import io.harness.gitsync.common.impl.ScmDelegateFacilitatorServiceImpl;
import io.harness.gitsync.common.impl.ScmFacilitatorServiceImpl;
import io.harness.gitsync.common.impl.ScmManagerFacilitatorServiceImpl;
import io.harness.gitsync.common.impl.ScmOrchestratorServiceImpl;
import io.harness.gitsync.common.impl.UserSourceCodeManagerServiceImpl;
import io.harness.gitsync.common.impl.YamlGitConfigServiceImpl;
import io.harness.gitsync.common.impl.gittoharness.GitToHarnessProcessorServiceImpl;
import io.harness.gitsync.common.mappers.AzureRepoSCMMapper;
import io.harness.gitsync.common.mappers.GithubSCMMapper;
import io.harness.gitsync.common.mappers.GitlabSCMMapper;
import io.harness.gitsync.common.mappers.UserSourceCodeManagerMapper;
import io.harness.gitsync.common.service.FullSyncTriggerService;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.GitEntityService;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.GitToHarnessProgressService;
import io.harness.gitsync.common.service.HarnessToGitHelperService;
import io.harness.gitsync.common.service.ScmClientFacilitatorService;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.UserSourceCodeManagerService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.common.service.gittoharness.GitToHarnessProcessorService;
import io.harness.gitsync.core.fullsync.FullSyncAccumulatorService;
import io.harness.gitsync.core.fullsync.FullSyncAccumulatorServiceImpl;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigService;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigServiceImpl;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityService;
import io.harness.gitsync.core.fullsync.GitFullSyncEntityServiceImpl;
import io.harness.gitsync.core.fullsync.GitFullSyncProcessorService;
import io.harness.gitsync.core.fullsync.GitFullSyncProcessorServiceImpl;
import io.harness.gitsync.core.fullsync.impl.FullSyncJobServiceImpl;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.core.impl.GitCommitServiceImpl;
import io.harness.gitsync.core.impl.GitSyncTriggerServiceImpl;
import io.harness.gitsync.core.impl.YamlChangeSetLifeCycleManagerServiceImpl;
import io.harness.gitsync.core.impl.YamlChangeSetServiceImpl;
import io.harness.gitsync.core.impl.webhookevent.GitBranchHookEventExecutionServiceImpl;
import io.harness.gitsync.core.impl.webhookevent.GitPushEventExecutionServiceImpl;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.gitsync.core.service.GitSyncTriggerService;
import io.harness.gitsync.core.service.YamlChangeSetLifeCycleManagerService;
import io.harness.gitsync.core.service.YamlChangeSetService;
import io.harness.gitsync.core.service.webhookevent.GitBranchHookEventExecutionService;
import io.harness.gitsync.core.service.webhookevent.GitPushEventExecutionService;
import io.harness.gitsync.event.GitCommitEventListener;
import io.harness.gitsync.event.GitToHarnessEventListener;
import io.harness.gitsync.event.YamlChangeSetEventListener;
import io.harness.gitsync.gitfileactivity.impl.GitSyncServiceImpl;
import io.harness.gitsync.gitfileactivity.service.GitSyncService;
import io.harness.gitsync.gitsyncerror.impl.GitSyncErrorServiceImpl;
import io.harness.gitsync.gitsyncerror.service.GitSyncErrorService;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.ng.core.event.MessageListener;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.persistence.HPersistence;
import io.harness.threading.ThreadPool;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@OwnedBy(DX)
public class GitSyncModule extends AbstractModule {
  private static GitSyncModule gitSyncModule;
  public static final String SCM_ON_MANAGER = "scmOnManager";
  public static final String SCM_ON_DELEGATE = "scmOnDelegate";
  public static final String GITX_BACKGROUND_CACHE_UPDATE_EXECUTOR_NAME = "gitxBackgroundCacheUpdateExecutorName";
  private final GitServiceConfiguration gitServiceConfiguration;

  private GitSyncModule(GitServiceConfiguration gitServiceConfiguration) {
    this.gitServiceConfiguration = gitServiceConfiguration;
  }

  public static GitSyncModule getInstance(GitServiceConfiguration gitServiceConfiguration) {
    if (gitSyncModule == null) {
      gitSyncModule = new GitSyncModule(gitServiceConfiguration);
    }
    return gitSyncModule;
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

  @Override
  protected void configure() {
    registerRequiredBindings();
    install(SCMJavaClientModule.getInstance());
    install(GitSyncSdkGrpcClientModule.getInstance());
    install(PrimaryVersionManagerModule.getInstance());
    bind(YamlGitConfigService.class).to(YamlGitConfigServiceImpl.class);
    bind(YamlChangeSetService.class).to(YamlChangeSetServiceImpl.class);
    bind(GitCommitService.class).to(GitCommitServiceImpl.class);
    bind(GitSyncService.class).to(GitSyncServiceImpl.class);
    bind(GitSyncErrorService.class).to(GitSyncErrorServiceImpl.class);
    bind(GitBranchService.class).to(GitBranchServiceImpl.class);
    bind(GitEntityService.class).to(GitEntityServiceImpl.class);
    bind(GitSyncTriggerService.class).to(GitSyncTriggerServiceImpl.class);
    bind(HarnessToGitHelperService.class).to(HarnessToGitHelperServiceImpl.class);
    bind(GitToHarnessProcessorService.class).to(GitToHarnessProcessorServiceImpl.class);
    bind(GitSyncSettingsService.class).to(GitSyncSettingsServiceImpl.class);
    bind(GitBranchHookEventExecutionService.class).to(GitBranchHookEventExecutionServiceImpl.class);
    bind(GitPushEventExecutionService.class).to(GitPushEventExecutionServiceImpl.class);
    bind(ScmClientFacilitatorService.class)
        .annotatedWith(Names.named(SCM_ON_MANAGER))
        .to(ScmManagerFacilitatorServiceImpl.class);
    bind(ScmClientFacilitatorService.class)
        .annotatedWith(Names.named(SCM_ON_DELEGATE))
        .to(ScmDelegateFacilitatorServiceImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("gitChangeSet"))
        .toInstance(new ManagedScheduledExecutorService("GitChangeSet"));
    bind(ScmOrchestratorService.class).to(ScmOrchestratorServiceImpl.class);
    bind(GitBranchSyncService.class).to(GitBranchSyncServiceImpl.class);
    bind(GitToHarnessProgressService.class).to(GitToHarnessProgressServiceImpl.class);
    bind(YamlChangeSetLifeCycleManagerService.class).to(YamlChangeSetLifeCycleManagerServiceImpl.class);
    bind(FullSyncAccumulatorService.class).to(FullSyncAccumulatorServiceImpl.class);
    bind(FullSyncJobService.class).to(FullSyncJobServiceImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitFullSyncEntityService.class).to(GitFullSyncEntityServiceImpl.class);
    bind(GitFullSyncProcessorService.class).to(GitFullSyncProcessorServiceImpl.class);
    bind(ScmFacilitatorService.class).to(ScmFacilitatorServiceImpl.class);
    bind(UserSourceCodeManagerService.class).to(UserSourceCodeManagerServiceImpl.class);
    bind(FullSyncTriggerService.class).to(FullSyncTriggerServiceImpl.class);
    bind(GitFullSyncConfigService.class).to(GitFullSyncConfigServiceImpl.class);
    bind(GitFileCacheService.class).to(GitFileCacheServiceImpl.class);
    bind(MessageListener.class).annotatedWith(Names.named(GIT_COMMIT + ENTITY_CRUD)).to(GitCommitEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(YAML_CHANGE_SET + ENTITY_CRUD))
        .to(YamlChangeSetEventListener.class);
    bind(MessageListener.class)
        .annotatedWith(Names.named(GIT_TO_HARNESS_PROGRESS + ENTITY_CRUD))
        .to(GitToHarnessEventListener.class);
    registerRequiredBindings();
    MapBinder<SCMType, UserSourceCodeManagerMapper> sourceCodeManagerMapBinder =
        MapBinder.newMapBinder(binder(), SCMType.class, UserSourceCodeManagerMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.GITHUB).to(GithubSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.GITLAB).to(GitlabSCMMapper.class);
    sourceCodeManagerMapBinder.addBinding(SCMType.AZURE_REPO).to(AzureRepoSCMMapper.class);

    bindFullSyncMessageListeners();
  }

  private void bindFullSyncMessageListeners() {
    Multibinder<MessageListener> fullSyncMessageListener =
        Multibinder.newSetBinder(binder(), MessageListener.class, Names.named(GIT_FULL_SYNC_STREAM));
    fullSyncMessageListener.addBinding().to(FullSyncMessageListener.class);
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }

  @Provides
  @Singleton
  @Named(GITX_BACKGROUND_CACHE_UPDATE_EXECUTOR_NAME)
  public ExecutorService backgroundCacheUpdateExecutorService() {
    return new ManagedExecutorService(ThreadPool.create(
        gitServiceConfiguration.getGitServiceCacheConfiguration().getBackgroundUpdateThreadPoolConfig(), 1,
        new ThreadFactoryBuilder().setNameFormat("GitxCachingBackgroundUpdateThread-%d").build(),
        new ThreadPoolExecutor.AbortPolicy()));
  }
}
