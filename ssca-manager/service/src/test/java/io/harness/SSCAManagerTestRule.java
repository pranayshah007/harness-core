/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static org.mockito.Mockito.mock;
import static org.modelmapper.convention.MatchingStrategies.STRICT;

import io.harness.factory.ClosingFactory;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.repositories.ArtifactRepository;
import io.harness.repositories.EnforcementResultRepo;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.rule.InjectorRuleMixin;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.spec.server.ssca.v1.EnforcementResultApi;
import io.harness.spec.server.ssca.v1.EnforcementSummaryApi;
import io.harness.spec.server.ssca.v1.NormalizeSbomApi;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.TokenApi;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.ssca.api.EnforcementResultApiImpl;
import io.harness.ssca.api.EnforcementSummaryApiImpl;
import io.harness.ssca.api.NormalizedSbomApiImpl;
import io.harness.ssca.api.SbomProcessorApiImpl;
import io.harness.ssca.api.TokenApiImpl;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.serializer.SSCAManagerModuleRegistrars;
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
import io.harness.ssca.services.NormalizeSbomService;
import io.harness.ssca.services.NormalizeSbomServiceImpl;
import io.harness.ssca.services.ProcessSbomWorkflowService;
import io.harness.ssca.services.ProcessSbomWorkflowServiceImpl;
import io.harness.ssca.services.RuleEngineService;
import io.harness.ssca.services.RuleEngineServiceImpl;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;
import io.harness.time.TimeModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.modelmapper.ModelMapper;
import org.modelmapper.PropertyMap;
import org.springframework.core.convert.converter.Converter;

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

      @Provides
      @Singleton
      @Named("sscaManagerServiceSecret")
      public String sscaManagerServiceSecret() {
        return "sscaManagerServiceSecret";
      }

      @Provides
      @Singleton
      public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(STRICT);

        PropertyMap<NormalizedSBOMComponentEntity, NormalizedSbomComponentDTO> normalizedSbomComponentDTOPropertyMap =
            new PropertyMap<>() {
              @Override
              protected void configure() {
                map().setCreated(new BigDecimal(source.getCreatedOn().toEpochMilli()));
              }
            };
        modelMapper.addMappings(normalizedSbomComponentDTOPropertyMap);

        PropertyMap<NormalizedSbomComponentDTO, NormalizedSBOMComponentEntity>
            normalizedSBOMComponentEntityPropertyMap = new PropertyMap<>() {
              @Override
              protected void configure() {
                map().setCreatedOn(Instant.ofEpochMilli(source.getCreated().longValue()));
                map().setAccountId(source.getAccountId() + "testing");
              }
            };
        modelMapper.addMappings(normalizedSBOMComponentEntityPropertyMap);

        return modelMapper;
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(SbomProcessorApi.class).to(SbomProcessorApiImpl.class);
        bind(NormalizeSbomApi.class).to(NormalizedSbomApiImpl.class);
        bind(EnforcementResultApi.class).to(EnforcementResultApiImpl.class);
        bind(EnforcementSummaryApi.class).to(EnforcementSummaryApiImpl.class);
        bind(ArtifactService.class).to(ArtifactServiceImpl.class);
        bind(ProcessSbomWorkflowService.class).to(ProcessSbomWorkflowServiceImpl.class);
        bind(EnforceSBOMWorkflowService.class).to(EnforceSBOMWorkflowServiceImpl.class);
        bind(RuleEngineService.class).to(RuleEngineServiceImpl.class);
        bind(NormalizeSbomService.class).to(NormalizeSbomServiceImpl.class);
        bind(EnforcementResultService.class).to(EnforcementResultServiceImpl.class);
        bind(EnforcementSummaryService.class).to(EnforcementSummaryServiceImpl.class);
        bind(NextGenService.class).toInstance(mock(NextGenServiceImpl.class));
        bind(SBOMComponentRepo.class).toInstance(mock(SBOMComponentRepo.class));
        bind(ArtifactRepository.class).toInstance(mock(ArtifactRepository.class));
        bind(EnforcementResultRepo.class).toInstance(mock(EnforcementResultRepo.class));
        bind(EnforcementSummaryRepo.class).toInstance(mock(EnforcementSummaryRepo.class));
        bind(TokenApi.class).to(TokenApiImpl.class);
      }
    });
    modules.add(TimeModule.getInstance());
    modules.add(TestMongoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
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
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object o) {
    return applyInjector(log, statement, frameworkMethod, o);
  }
}
