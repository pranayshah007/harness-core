/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.authorization.AuthorizationServiceHeader.BATCH_PROCESSING;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.ccm.eventframework.CENGEventConsumerService;
import io.harness.ccm.migration.CENGCoreMigrationProvider;
import io.harness.ccm.service.impl.PerspectivesRestrictionUsageImpl;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.resources.EnforcementClientResource;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ff.FeatureFlagConfig;
import io.harness.ff.FeatureFlagService;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.licensing.usage.resources.LicenseUsageResource;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.request.RequestContextFilter;
import io.harness.resource.VersionInfoResource;
import io.harness.secret.ConfigSecretUtils;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.threading.ExecutorModule;
import io.harness.threading.ThreadPool;
import io.harness.token.remote.TokenClient;
import io.harness.yaml.YamlSdkConfiguration;
import io.harness.yaml.YamlSdkInitHelper;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
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
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.serializer.HObjectMapper;
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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(CE)
public class CENextGenApplication extends Application<CENextGenConfiguration> {
  private static final String APPLICATION_NAME = "CE NextGen Microservice";
  private final MetricRegistry metricRegistry = new MetricRegistry();

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new CENextGenApplication().run(args);
  }

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<CENextGenConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(getConfigurationProvider(bootstrap.getConfigurationSourceProvider()));
    bootstrap.addBundle(getSwaggerBundle());
    bootstrap.setMetricRegistry(metricRegistry);
    configureObjectMapper(bootstrap.getObjectMapper());
  }

  private static SubstitutingSourceProvider getConfigurationProvider(ConfigurationSourceProvider sourceProvider) {
    return new SubstitutingSourceProvider(sourceProvider, new EnvironmentVariableSubstitutor(false));
  }

  private static SwaggerBundle<CENextGenConfiguration> getSwaggerBundle() {
    return new SwaggerBundle<CENextGenConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(CENextGenConfiguration appConfig) {
        return appConfig.getSwaggerBundleConfiguration();
      }
    };
  }

  public static void configureObjectMapper(final ObjectMapper mapper) {
    HObjectMapper.configureObjectMapperForNG(mapper);
  }

  @Override
  public void run(CENextGenConfiguration configuration, Environment environment) throws Exception {
    ExecutorModule.getInstance().setExecutorService(ThreadPool.create(
        20, 100, 500L, TimeUnit.MILLISECONDS, new ThreadFactoryBuilder().setNameFormat("main-app-pool-%d").build()));
    log.info("Starting CE NextGen Application ...");
    MaintenanceController.forceMaintenance(true);

    ConfigSecretUtils.resolveSecrets(configuration.getSecretsConfiguration(), configuration);

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return configuration.getDbAliases();
      }
    });
    modules.add(new CENextGenModule(configuration));
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MetricRegistry.class).toInstance(metricRegistry);
      }
    });
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(NGMigrationSdkModule.getInstance());

    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return configuration.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return configuration.getFeatureFlagConfig();
      }
    });
    Injector injector = Guice.createInjector(modules);

    // create collection and indexes
    injector.getInstance(HPersistence.class);

    registerAuthFilters(configuration, environment, injector);
    registerRequestContextFilter(environment);
    registerJerseyFeatures(environment);
    registerCorsFilter(configuration, environment);
    registerResources(environment, injector);
    initializeFeatureFlags(configuration, injector);
    registerHealthCheck(environment, injector);
    registerExceptionMappers(environment.jersey());
    registerCorrelationFilter(environment, injector);
    registerScheduledJobs(injector);
    registerMigrations(injector);
    registerOasResource(configuration, environment, injector);
    registerYamlSdk(injector);

    if (BooleanUtils.isTrue(configuration.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    MaintenanceController.forceMaintenance(false);
    createConsumerThreadsToListenToEvents(environment, injector);
    initializeEnforcementSdk(injector);
    initializeMonitoring(injector);
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerOasResource(CENextGenConfiguration configuration, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(configuration.getOasConfig());

    environment.jersey().register(openApiResource);
  }

  private void registerExceptionMappers(JerseyEnvironment jersey) {
    jersey.register(JerseyViolationExceptionMapperV2.class);
    jersey.register(WingsExceptionMapperV2.class);
    jersey.register(GenericExceptionMapperV2.class);
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("application", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerScheduledJobs(Injector injector) {
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : CENextGenConfiguration.getResourceClasses()) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
    environment.jersey().register(injector.getInstance(LicenseUsageResource.class));
    environment.jersey().register(injector.getInstance(EnforcementClientResource.class));
  }

  private void registerAuthFilters(CENextGenConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isEnableAuth()) {
      registerNextGenAuthFilter(configuration, environment, injector);

      registerInternalApiAuthFilter(configuration, environment);
    }
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthenticationExemptedRequestsPredicate() {
    return getAuthFilterPredicate(PublicApi.class)
        .or(resourceInfoAndRequest
            -> resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/version")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/openapi.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith(
                    "/swagger.yaml"));
  }

  private void registerNextGenAuthFilter(
      CENextGenConfiguration configuration, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        (getAuthenticationExemptedRequestsPredicate().negate())
            .and((getAuthFilterPredicate(InternalApi.class)).negate());

    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.IDENTITY_SERVICE.getServiceId(), configuration.getJwtIdentityServiceSecret());
    serviceToSecretMapping.put(AuthorizationServiceHeader.BEARER.getServiceId(), configuration.getJwtAuthSecret());
    serviceToSecretMapping.put(
        AuthorizationServiceHeader.DEFAULT.getServiceId(), configuration.getNgManagerServiceSecret());

    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
  }

  private void registerInternalApiAuthFilter(CENextGenConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getNgManagerServiceSecret());
    serviceToSecretMapping.put(BATCH_PROCESSING.getServiceId(), configuration.getNgManagerServiceSecret());

    environment.jersey().register(
        new InternalApiAuthFilter(getAuthFilterPredicate(InternalApi.class), null, serviceToSecretMapping));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthFilterPredicate(
      Class<? extends Annotation> annotation) {
    return resourceInfoAndRequest
        -> (resourceInfoAndRequest.getKey().getResourceMethod() != null
               && resourceInfoAndRequest.getKey().getResourceMethod().getAnnotation(annotation) != null)
        || (resourceInfoAndRequest.getKey().getResourceClass() != null
            && resourceInfoAndRequest.getKey().getResourceClass().getAnnotation(annotation) != null);
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerCorsFilter(CENextGenConfiguration configuration, Environment environment) {
    FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", configuration.getAllowedOrigins());
    cors.setInitParameters(ImmutableMap.of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void initializeFeatureFlags(CENextGenConfiguration configuration, Injector injector) {
    injector.getInstance(FeatureFlagService.class)
        .initializeFeatureFlags(configuration.getDeployMode(), configuration.getFeatureFlagsEnabled());
  }

  private void createConsumerThreadsToListenToEvents(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(CENGEventConsumerService.class));
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.CE) // this is only for locking purpose
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(CENGCoreMigrationProvider.class); } // Add all migration provider classes here
        })
        .build();
  }

  private void initializeEnforcementSdk(Injector injector) {
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder()
                    .put(FeatureRestrictionName.PERSPECTIVES, PerspectivesRestrictionUsageImpl.class)
                    .build())
            .build();

    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder().build())
            .build();

    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }

  private void initializeMonitoring(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private void registerYamlSdk(Injector injector) {
    YamlSdkConfiguration yamlSdkConfiguration = YamlSdkConfiguration.builder()
                                                    .requireSchemaInit(true)
                                                    .requireSnippetInit(true)
                                                    .requireValidatorInit(true)
                                                    .build();
    YamlSdkInitHelper.initialize(injector, yamlSdkConfiguration);
  }
}
