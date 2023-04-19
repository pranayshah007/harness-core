/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static io.harness.CvNextGenTestBase.getResourceFilePath;
import static io.harness.authorization.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.cache.CacheBackend.CAFFEINE;
import static io.harness.cache.CacheBackend.NOOP;

import io.harness.AccessControlClientConfiguration;
import io.harness.AccessControlClientModule;
import io.harness.SRMMongoPersistence;
import io.harness.SRMPersistence;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.AccountClient;
import io.harness.cache.CacheConfig;
import io.harness.cache.CacheConfig.CacheConfigBuilder;
import io.harness.cache.CacheModule;
import io.harness.cvng.CVNGTestConstants;
import io.harness.cvng.CVNextGenCommonsServiceModule;
import io.harness.cvng.CVServiceModule;
import io.harness.cvng.EventsFrameworkModule;
import io.harness.cvng.VerificationConfiguration;
import io.harness.cvng.client.ErrorTrackingClient;
import io.harness.cvng.client.ErrorTrackingService;
import io.harness.cvng.client.FakeAccessControlClient;
import io.harness.cvng.client.FakeAccountClient;
import io.harness.cvng.client.FakeNextGenService;
import io.harness.cvng.client.FakeNotificationClient;
import io.harness.cvng.client.MockedVerificationManagerService;
import io.harness.cvng.client.NextGenClientModule;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerClientModule;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.NGManagerServiceConfig;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.impl.AlwaysFalseFeatureFlagServiceImpl;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.factory.ClosingFactory;
import io.harness.factory.ClosingFactoryModule;
import io.harness.govern.ProviderModule;
import io.harness.govern.ServersModule;
import io.harness.lock.PersistentLockModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.notification.MongoBackendConfiguration;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.notification.constant.NotificationClientSecrets;
import io.harness.notification.module.NotificationClientModule;
import io.harness.notification.module.NotificationClientPersistenceModule;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.opaclient.OpaServiceClient;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.api.impl.OutboxDaoImpl;
import io.harness.outbox.api.impl.OutboxServiceImpl;
import io.harness.persistence.HPersistence;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.outbox.OutboxEventRepository;
import io.harness.serializer.CvNextGenRegistrars;
import io.harness.serializer.KryoModule;
import io.harness.serializer.KryoRegistrar;
import io.harness.springdata.SpringPersistenceTestModule;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.testlib.module.MongoRuleMixin;
import io.harness.testlib.module.TestMongoModule;
import io.harness.threading.CurrentThreadExecutor;
import io.harness.threading.ExecutorModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import dev.morphia.converters.TypeConverter;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import io.serializer.HObjectMapper;
import java.io.Closeable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.core.convert.converter.Converter;
import org.yaml.snakeyaml.Yaml;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
public class CvNextGenRule implements MethodRule, InjectorRuleMixin, MongoRuleMixin {
  ClosingFactory closingFactory;
  private boolean forbiddenAccessControl;

  public CvNextGenRule(ClosingFactory closingFactory, boolean forbiddenAccessControl) {
    this.closingFactory = closingFactory;
    this.forbiddenAccessControl = forbiddenAccessControl;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    ExecutorModule.getInstance().setExecutorService(new CurrentThreadExecutor());

    List<Module> modules = new ArrayList<>();
    modules.add(new ClosingFactoryModule(closingFactory));
    modules.add(KryoModule.getInstance());
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
            .addAll(CvNextGenRegistrars.kryoRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
        return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
            .addAll(CvNextGenRegistrars.morphiaRegistrars)
            .build();
      }

      @Provides
      @Singleton
      Set<Class<? extends TypeConverter>> morphiaConverters() {
        return ImmutableSet.<Class<? extends TypeConverter>>builder()
            .addAll(CvNextGenRegistrars.morphiaConverters)
            .build();
      }

      @Provides
      @Singleton
      List<Class<? extends Converter<?, ?>>> springConverters() {
        return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
            .addAll(CvNextGenRegistrars.springConverters)
            .build();
      }
    });

    modules.add(TestMongoModule.getInstance());
    modules.add(mongoTypeModule(annotations));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(HPersistence.class).to(MongoPersistence.class);
        bind(SRMPersistence.class).to(SRMMongoPersistence.class);
      }
    });

    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      MongoConfig mongoConfig() {
        return MongoConfig.builder().build();
      }
    });

    modules.add(new CVNextGenCommonsServiceModule());

    modules.add(TestMongoModule.getInstance());

    VerificationConfiguration verificationConfiguration = getVerificationConfiguration();
    modules.add(Modules.override(new CVServiceModule(verificationConfiguration)).with(binder -> {
      binder.bind(FeatureFlagService.class).to(AlwaysFalseFeatureFlagServiceImpl.class);
      binder.bind(VerificationManagerService.class).to(MockedVerificationManagerService.class);
      binder.bind(Clock.class).toInstance(CVNGTestConstants.FIXED_TIME_FOR_TESTS);
      binder.bind(TemplateResourceClient.class).toInstance(getMockedTemplateResourceClient());
      binder.bind(NextGenService.class).to(FakeNextGenService.class);
      binder.bind(ErrorTrackingService.class).toInstance(Mockito.mock(ErrorTrackingService.class));
      binder.bind(ErrorTrackingClient.class).toInstance(Mockito.mock(ErrorTrackingClient.class));
      binder.bind(AccountClient.class).to(FakeAccountClient.class);
      binder.bind(EnforcementClientService.class).toInstance(Mockito.mock(EnforcementClientService.class));
      binder.bind(OpaServiceClient.class).toInstance(Mockito.mock(OpaServiceClient.class));
    }));
    MongoBackendConfiguration mongoBackendConfiguration =
        MongoBackendConfiguration.builder().uri("mongodb://localhost:27017/notificationChannel").build();
    modules.add(new EventsFrameworkModule(verificationConfiguration.getEventsFrameworkConfiguration()));
    mongoBackendConfiguration.setType("MONGO");
    modules.add(Modules.override(new SpringPersistenceTestModule()).with(new NotificationClientPersistenceModule()));
    modules.add(new NextGenClientModule(
        NGManagerServiceConfig.builder().managerServiceSecret("secret").ngManagerUrl("http://test-ng-host").build()));
    modules.add(new VerificationManagerClientModule("http://test-host"));
    modules.add(new MetricsModule());
    modules.add(new PersistentLockModule());

    CacheConfigBuilder cacheConfigBuilder =
        CacheConfig.builder().disabledCaches(new HashSet<>()).cacheNamespace("harness-cache");
    if (annotations.stream().anyMatch(annotation -> annotation instanceof Cache)) {
      cacheConfigBuilder.cacheBackend(CAFFEINE);
    } else {
      cacheConfigBuilder.cacheBackend(NOOP);
    }
    CacheModule cacheModule = new CacheModule(cacheConfigBuilder.build());
    modules.add(cacheModule);
    AccessControlClientConfiguration accessControlClientConfiguration =
        AccessControlClientConfiguration.builder()
            .enableAccessControl(false)
            .accessControlServiceSecret("token")
            .accessControlServiceConfig(ServiceHttpClientConfig.builder()
                                            .baseUrl("http://localhost:9006/api/")
                                            .readTimeOutSeconds(15)
                                            .connectTimeOutSeconds(15)
                                            .build())
            .build();
    modules.add(Modules
                    .override(AccessControlClientModule.getInstance(
                        accessControlClientConfiguration, CV_NEXT_GEN.getServiceId()))
                    .with(binder -> {
                      if (forbiddenAccessControl) {
                        binder.bind(AccessControlClient.class).to(FakeAccessControlClient.class);
                      }
                    }));
    NotificationClientConfiguration notificationClientConfiguration =
        NotificationClientConfiguration.builder()
            .notificationClientBackendConfiguration(mongoBackendConfiguration)
            .serviceHttpClientConfig(ServiceHttpClientConfig.builder()
                                         .baseUrl("http://localhost:9005/api/")
                                         .connectTimeOutSeconds(15)
                                         .readTimeOutSeconds(15)
                                         .build())
            .notificationSecrets(
                NotificationClientSecrets.builder()
                    .notificationClientSecret(
                        "IC04LYMBf1lDP5oeY4hupxd4HJhLmN6azUku3xEbeE3SUx5G3ZYzhbiwVtK4i7AmqyU9OZkwB4v8E9qM")
                    .build())
            .build();
    modules.add(Modules.override(new NotificationClientModule(notificationClientConfiguration))
                    .with(binder -> binder.bind(NotificationClient.class).to(FakeNotificationClient.class)));
    return modules;
  }

  private VerificationConfiguration getVerificationConfiguration()
      throws java.io.IOException, io.dropwizard.configuration.ConfigurationException {
    final ObjectMapper objectMapper = Jackson.newObjectMapper();
    final Validator validator = Validators.newValidator();
    final YamlConfigurationFactory<VerificationConfiguration> factory =
        new YamlConfigurationFactory<>(VerificationConfiguration.class, validator, objectMapper, "dw");

    // TODO: once we move completely on bazel, we can get rid of mvn way
    final File yaml = new File(getResourceFilePath("test-config.yml"));
    return factory.build(yaml);
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
  public Statement apply(Statement statement, FrameworkMethod frameworkMethod, Object target) {
    return applyInjector(log, statement, frameworkMethod, target);
  }

  private TemplateResourceClient getMockedTemplateResourceClient() {
    TemplateResourceClient templateResourceClient = Mockito.mock(TemplateResourceClient.class);
    Mockito
        .when(templateResourceClient.applyTemplatesOnGivenYamlV2(Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenAnswer((Answer<Call<ResponseDTO<TemplateMergeResponseDTO>>>) invocation -> {
          TemplateApplyRequestDTO templateApplyRequestDTO = (TemplateApplyRequestDTO) invocation.getArguments()[12];
          String yaml = templateApplyRequestDTO.getOriginalEntityYaml();
          Yaml yamlObject = new Yaml();
          Map<String, Object> data = yamlObject.load(yaml);
          TemplateReferenceSummary templateReferenceSummary =
              TemplateReferenceSummary.builder()
                  .templateIdentifier(getFromYamlMap(data, "monitoredService", "template", "templateRef"))
                  .versionLabel(getFromYamlMap(data, "monitoredService", "template", "versionLabel"))
                  .build();
          Call<ResponseDTO<TemplateMergeResponseDTO>> call = Mockito.mock(Call.class);
          Mockito.when(call.execute())
              .thenReturn(Response.success(ResponseDTO.newResponse(
                  TemplateMergeResponseDTO.builder()
                      .mergedPipelineYaml(yaml)
                      .templateReferenceSummaries(Collections.singletonList(templateReferenceSummary))
                      .build())));
          return call;
        });
    return templateResourceClient;
  }

  private String getFromYamlMap(Map<String, Object> data, String... paths) {
    for (int i = 0; i < paths.length - 1; i++) {
      if (!data.containsKey(paths[i])) {
        return null;
      }
      data = (Map<String, Object>) data.get(paths[i]);
    }
    return (String) data.get(paths[paths.length - 1]);
  }

  @Provides
  @Singleton
  OutboxService getOutboxService(OutboxEventRepository outboxEventRepository) {
    return new OutboxServiceImpl(new OutboxDaoImpl(outboxEventRepository), HObjectMapper.NG_DEFAULT_OBJECT_MAPPER);
  }
}
