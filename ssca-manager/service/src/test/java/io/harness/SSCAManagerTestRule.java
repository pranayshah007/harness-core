/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static org.mockito.Mockito.mock;

import io.harness.factory.ClosingFactory;
import io.harness.govern.ProviderModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.repositories.ArtifactRepository;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.repositories.EnforcementResultRepo;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.SSCAManagerModuleRegistrars;
import io.harness.spec.server.ssca.v1.EnforcementApi;
import io.harness.spec.server.ssca.v1.OrchestrationApi;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.TokenApi;
import io.harness.ssca.S3Config;
import io.harness.ssca.api.EnforcementApiImpl;
import io.harness.ssca.api.OrchestrationApiImpl;
import io.harness.ssca.api.SbomProcessorApiImpl;
import io.harness.ssca.api.TokenApiImpl;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.ArtifactServiceImpl;
import io.harness.ssca.services.CdInstanceSummaryService;
import io.harness.ssca.services.CdInstanceSummaryServiceImpl;
import io.harness.ssca.services.EnforcementResultService;
import io.harness.ssca.services.EnforcementResultServiceImpl;
import io.harness.ssca.services.EnforcementStepService;
import io.harness.ssca.services.EnforcementStepServiceImpl;
import io.harness.ssca.services.EnforcementSummaryService;
import io.harness.ssca.services.EnforcementSummaryServiceImpl;
import io.harness.ssca.services.NextGenService;
import io.harness.ssca.services.NextGenServiceImpl;
import io.harness.ssca.services.NormalisedSbomComponentService;
import io.harness.ssca.services.NormalisedSbomComponentServiceImpl;
import io.harness.ssca.services.OrchestrationStepService;
import io.harness.ssca.services.OrchestrationStepServiceImpl;
import io.harness.ssca.services.RuleEngineService;
import io.harness.ssca.services.RuleEngineServiceImpl;
import io.harness.ssca.services.S3StoreService;
import io.harness.ssca.services.S3StoreServiceImpl;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class SSCAManagerTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;

  public SSCAManagerTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());
    List<Module> modules = new ArrayList<>();
    modules.add(KryoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      public S3Config s3Config() {
        return S3Config.builder().build();
      }

      @Provides
      @Singleton
      public AmazonS3 s3Client() {
        return AmazonS3ClientBuilder.standard().build();
      }

      @Provides
      @Singleton
      @Named("jwtAuthSecret")
      public String jwtAuthSecret() {
        return "jstAuthSecret";
      }

      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(SSCAManagerModuleRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(SSCAManagerModuleRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(SSCAManagerModuleRegistrars.morphiaConverters)
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
            .addAll(SSCAManagerModuleRegistrars.springConverters)
            .build();
      }

      @Provides
      @Named("disableDeserialization")
      @Singleton
      public boolean getSerializationForDelegate() {
        return false;
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(SbomProcessorApi.class).to(SbomProcessorApiImpl.class);
        bind(EnforcementApi.class).to(EnforcementApiImpl.class);
        bind(OrchestrationApi.class).to(OrchestrationApiImpl.class);
        bind(ArtifactService.class).to(ArtifactServiceImpl.class);
        bind(OrchestrationStepService.class).to(OrchestrationStepServiceImpl.class);
        bind(EnforcementStepService.class).to(EnforcementStepServiceImpl.class);
        bind(RuleEngineService.class).to(RuleEngineServiceImpl.class);
        bind(NormalisedSbomComponentService.class).to(NormalisedSbomComponentServiceImpl.class);
        bind(EnforcementResultService.class).to(EnforcementResultServiceImpl.class);
        bind(EnforcementSummaryService.class).to(EnforcementSummaryServiceImpl.class);
        bind(NextGenService.class).toInstance(mock(NextGenServiceImpl.class));
        bind(SBOMComponentRepo.class).toInstance(mock(SBOMComponentRepo.class));
        bind(ArtifactRepository.class).toInstance(mock(ArtifactRepository.class));
        bind(EnforcementResultRepo.class).toInstance(mock(EnforcementResultRepo.class));
        bind(EnforcementSummaryRepo.class).toInstance(mock(EnforcementSummaryRepo.class));
        bind(CdInstanceSummaryRepo.class).toInstance(mock(CdInstanceSummaryRepo.class));
        bind(CdInstanceSummaryService.class).to(CdInstanceSummaryServiceImpl.class);
        bind(S3StoreService.class).to(S3StoreServiceImpl.class);
        bind(TokenApi.class).to(TokenApiImpl.class);
        bind(MongoTemplate.class).toInstance(mock(MongoTemplate.class));
      }
    });
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
    return modules;
  }

  @Override
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    return applyInjector(log, statement, frameworkMethod, o);
  }
}
