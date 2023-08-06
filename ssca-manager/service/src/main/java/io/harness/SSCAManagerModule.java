/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.SSCAManagerModuleRegistrars;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.ssca.api.SbomProcessorApiImpl;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.ArtifactServiceImpl;
import io.harness.ssca.services.EnforceSBOMWorkflowService;
import io.harness.ssca.services.EnforceSBOMWorkflowServiceImpl;
import io.harness.ssca.services.EnforcementResultService;
import io.harness.ssca.services.EnforcementResultServiceImpl;
import io.harness.ssca.services.EnforcementSummaryService;
import io.harness.ssca.services.EnforcementSummaryServiceImpl;
import io.harness.ssca.services.NextGenService;
import io.harness.ssca.services.NextGenServiceImpl;
import io.harness.ssca.services.ProcessSbomWorkflowService;
import io.harness.ssca.services.ProcessSbomWorkflowServiceImpl;
import io.harness.ssca.services.RuleEngineService;
import io.harness.ssca.services.RuleEngineServiceImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(SSCA)
public class SSCAManagerModule extends AbstractModule {
  private final io.harness.SSCAManagerConfiguration configuration;

  private static SSCAManagerModule sscaManagerModule;
  private SSCAManagerModule(io.harness.SSCAManagerConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    install(new io.harness.SSCAManagerModulePersistence());
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(SbomProcessorApi.class).to(SbomProcessorApiImpl.class);
    bind(ArtifactService.class).to(ArtifactServiceImpl.class);
    bind(ProcessSbomWorkflowService.class).to(ProcessSbomWorkflowServiceImpl.class);
    bind(EnforceSBOMWorkflowService.class).to(EnforceSBOMWorkflowServiceImpl.class);
    bind(RuleEngineService.class).to(RuleEngineServiceImpl.class);
    bind(EnforcementResultService.class).to(EnforcementResultServiceImpl.class);
    bind(EnforcementSummaryService.class).to(EnforcementSummaryServiceImpl.class);
    bind(NextGenService.class).to(NextGenServiceImpl.class);
  }

  @Provides
  @Singleton
  @Named("ngManagerServiceHttpClientConfig")
  public ServiceHttpClientConfig ngManagerServiceHttpClientConfig() {
    return this.configuration.getNgManagerServiceHttpClientConfig();
  }
  @Provides
  @Singleton
  @Named("ngManagerServiceSecret")
  public String ngManagerServiceSecret() {
    return this.configuration.getNgManagerServiceSecret();
  }

  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(SSCAManagerModuleRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(SSCAManagerModuleRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder()
        .addAll(SSCAManagerModuleRegistrars.morphiaConverters)
        .build();
  }

  @Provides
  @Singleton
  List<Class<? extends Converter<?, ?>>> springConverters() {
    return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
        .addAll(SSCAManagerModuleRegistrars.springConverters)
        .build();
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return configuration.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return Collections.emptyMap();
  }

  public static SSCAManagerModule getInstance(io.harness.SSCAManagerConfiguration sscaManagerConfiguration) {
    if (sscaManagerModule == null) {
      return new SSCAManagerModule(sscaManagerConfiguration);
    }
    return sscaManagerModule;
  }
}
