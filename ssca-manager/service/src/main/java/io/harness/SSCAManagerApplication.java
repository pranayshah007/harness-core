/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.NGConstants.X_API_KEY;
import static io.harness.SSCAManagerConfiguration.getResourceClasses;
import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.collect.ImmutableMap.of;

import io.harness.annotations.SSCAAuthIfHasApiKey;
import io.harness.annotations.SSCAServiceAuth;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.cache.CacheModule;
import io.harness.changestreams.redisconsumers.InstanceNGRedisEventConsumer;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.govern.ProviderModule;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.modules.MetricsModule;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotAllowedExceptionMapper;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.ng.core.filter.ApiResponseFilter;
import io.harness.persistence.HPersistence;
import io.harness.request.RequestContextFilter;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.ssca.migration.SSCAMigrationProvider;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.token.remote.TokenClient;

import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(SSCA)
public class SSCAManagerApplication extends Application<SSCAManagerConfiguration> {
  private static final String APPLICATION_NAME = "SSCA Manager Application";

  private final MetricRegistry metricRegistry = new MetricRegistry();

  private HarnessMetricRegistry harnessMetricRegistry;

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));

    new SSCAManagerApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<SSCAManagerConfiguration> bootstrap) {
    initializeLogging();

    // bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addBundle(new SwaggerBundle<SSCAManagerConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(
          SSCAManagerConfiguration sscaManagerConfiguration) {
        return sscaManagerConfiguration.getSwaggerBundleConfiguration();
      }
    });
    bootstrap.setMetricRegistry(metricRegistry);
  }

  @Override
  public void run(SSCAManagerConfiguration sscaManagerConfiguration, Environment environment) throws Exception {
    log.info("Starting SSCA Manager Application ...");
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        20, 1000, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      SSCAManagerConfiguration configuration() {
        return sscaManagerConfiguration;
      }

      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return sscaManagerConfiguration.getDbAliases();
      }
    });

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetricRegistry.class).toInstance(metricRegistry);
      }
    });
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(NGMigrationSdkModule.getInstance());
    modules.add(new MetricsModule());
    CacheModule cacheModule = new CacheModule(sscaManagerConfiguration.getCacheConfig());
    modules.add(cacheModule);
    modules.add(io.harness.SSCAManagerModule.getInstance(sscaManagerConfiguration));
    MaintenanceController.forceMaintenance(true);
    Injector injector = Guice.createInjector(modules);
    injector.getInstance(HPersistence.class);
    registerJerseyProviders(environment, injector);
    registerResources(environment, injector);
    registerOasResource(sscaManagerConfiguration, environment, injector);
    registerAuthFilters(sscaManagerConfiguration, environment, injector);
    registerApiResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerRequestContextFilter(environment);
    registerCorsFilter(sscaManagerConfiguration, environment);
    registerSscaEvents(sscaManagerConfiguration, injector);
    registerManagedBeans(environment, injector);
    registerMigrations(injector);
    MaintenanceController.forceMaintenance(false);
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
    harnessMetricRegistry = injector.getInstance(HarnessMetricRegistry.class);
  }

  private void registerOasResource(
      SSCAManagerConfiguration sscaManagerConfiguration, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(sscaManagerConfiguration.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : getResourceClasses()) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
  }
  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }
  private void registerApiResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(ApiResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerCorsFilter(SSCAManagerConfiguration appConfig, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerJerseyProviders(Environment environment, Injector injector) {
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
    environment.jersey().register(new JsonProcessingExceptionMapper(true));
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(NotFoundExceptionMapper.class);
    environment.jersey().register(NotAllowedExceptionMapper.class);
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerAuthFilters(SSCAManagerConfiguration config, Environment environment, Injector injector) {
    if (config.isAuthEnabled()) {
      Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate = resourceInfoAndRequest
          -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
                 && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(SSCAServiceAuth.class) != null)
          || (resourceInfoAndRequest.getKey().getResourceClass() != null
              && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(SSCAServiceAuth.class) != null)
          || (resourceInfoAndRequest.getKey().getResourceMethod() != null
              && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(SSCAAuthIfHasApiKey.class) != null
              && resourceInfoAndRequest.getValue().getHeaders().get(X_API_KEY) != null)
          || (resourceInfoAndRequest.getKey().getResourceMethod() != null
              && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null)
          || (resourceInfoAndRequest.getKey().getResourceClass() != null
              && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(NextGenManagerAuth.class) != null);
      Map<String, String> serviceToSecretMapping = new HashMap<>();
      serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), config.getJwtAuthSecret());
      serviceToSecretMapping.put(
          AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), config.getJwtIdentityServiceSecret());
      serviceToSecretMapping.put(AuthorizationServiceHeader.DEFAULT.getServiceId(), config.getNgManagerServiceSecret());
      environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
          injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
      registerInternalApiAuthFilter(config, environment);
    }
  }

  private void registerInternalApiAuthFilter(SSCAManagerConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.DEFAULT.getServiceId(), configuration.getSscaManagerServiceSecret());
    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private void registerSscaEvents(SSCAManagerConfiguration appConfig, Injector injector) {
    SSCAEventConsumerController sscaEventConsumerController = injector.getInstance(SSCAEventConsumerController.class);
    sscaEventConsumerController.register(injector.getInstance(InstanceNGRedisEventConsumer.class),
        appConfig.getDebeziumConsumerConfigs().getInstanceNGConsumer().getThreads());
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    createConsumerThreadsToListenToEvents(environment, injector);
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.SSCA)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(SSCAMigrationProvider.class); } // Add all migration provider classes here
        })
        .build();
  }

  private void createConsumerThreadsToListenToEvents(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(SSCAEventConsumerController.class));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }
}
