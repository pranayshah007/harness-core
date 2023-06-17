/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.pms.sdk.core.SdkDeployMode.REMOTE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.HarnessCacheManager;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.execution.ExecutionSummaryModuleInfoProvider;
import io.harness.pms.sdk.core.pipeline.filters.FilterCreationResponseMerger;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoDecorator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoDecoratorImpl;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.resolver.expressions.EngineGrpcExpressionService;
import io.harness.version.VersionInfoManager;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;

@OwnedBy(HarnessTeam.PIPELINE)
class PmsSdkProviderModule extends AbstractModule {
  private final PmsSdkConfiguration config;

  private static PmsSdkProviderModule instance;

  public static PmsSdkProviderModule getInstance(PmsSdkConfiguration config) {
    if (instance == null) {
      instance = new PmsSdkProviderModule(config);
    }
    return instance;
  }

  private PmsSdkProviderModule(PmsSdkConfiguration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    if (config.getDeploymentMode() == REMOTE) {
      bind(EngineExpressionService.class).to(EngineGrpcExpressionService.class).in(Singleton.class);
    }
    if (config.getExecutionSummaryModuleInfoProviderClass() != null) {
      bind(ExecutionSummaryModuleInfoProvider.class)
          .to(config.getExecutionSummaryModuleInfoProviderClass())
          .in(Singleton.class);
    }
    if (config.getPipelineServiceInfoProviderClass() != null) {
      bind(PipelineServiceInfoProvider.class).to(config.getPipelineServiceInfoProviderClass()).in(Singleton.class);
      bind(PipelineServiceInfoDecorator.class).to(PipelineServiceInfoDecoratorImpl.class).in(Singleton.class);
    }
  }

  @Provides
  @Singleton
  public FilterCreationResponseMerger filterCreationResponseMerger() {
    return config.getFilterCreationResponseMerger();
  }

  @Provides
  @Singleton
  @Named("sdkEventsCache")
  public Cache<String, Integer> sdkEventsCache(
      HarnessCacheManager harnessCacheManager, VersionInfoManager versionInfoManager) {
    return harnessCacheManager.getCache("pmsSdkEventsCache", String.class, Integer.class,
        AccessedExpiryPolicy.factoryOf(Duration.THIRTY_MINUTES), versionInfoManager.getVersionInfo().getBuildNo(),
        true);
  }
}
