/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.accesscontrol.AccessControlConfiguration.ALL_ACCESS_CONTROL_RESOURCES;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.BEARER;
import static io.harness.authorization.AuthorizationServiceHeader.CI_MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.CODE;
import static io.harness.authorization.AuthorizationServiceHeader.CV_NEXT_GEN;
import static io.harness.authorization.AuthorizationServiceHeader.DEFAULT;
import static io.harness.authorization.AuthorizationServiceHeader.DELEGATE_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.IDENTITY_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.NOTIFICATION_SERVICE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.logging.LoggingInitializer.initializeLogging;

import static com.google.common.collect.ImmutableMap.of;
import static io.serializer.HObjectMapper.configureObjectMapperForNG;

import io.harness.Microservice;
import io.harness.accesscontrol.commons.bootstrap.AccessControlManagementJob;
import io.harness.accesscontrol.commons.events.EntityCrudEventListenerService;
import io.harness.accesscontrol.commons.events.UserMembershipEventListenerService;
import io.harness.accesscontrol.commons.migration.AccessControlMigrationProvider;
import io.harness.accesscontrol.commons.version.VersionInfoResource;
import io.harness.accesscontrol.principals.serviceaccounts.iterators.ServiceAccountReconciliationIterator;
import io.harness.accesscontrol.principals.usergroups.iterators.UserGroupReconciliationIterator;
import io.harness.accesscontrol.principals.users.iterators.UserReconciliationIterator;
import io.harness.accesscontrol.resources.resourcegroups.iterators.ResourceGroupReconciliationIterator;
import io.harness.accesscontrol.roleassignments.worker.ProjectOrgBasicRoleCreationService;
import io.harness.accesscontrol.roleassignments.worker.UserRoleAssignmentRemovalService;
import io.harness.accesscontrol.scopes.harness.iterators.ScopeReconciliationIterator;
import io.harness.accesscontrol.support.reconciliation.SupportPreferenceReconciliationIterator;
import io.harness.accesscontrol.support.reconciliation.SupportRoleAssignmentsReconciliationService;
import io.harness.aggregator.AggregatorService;
import io.harness.aggregator.MongoOffsetCleanupJob;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.AbstractCfModule;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.violation.ConstraintViolationExceptionMapper;
import io.harness.ff.FeatureFlagConfig;
import io.harness.govern.ProviderModule;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.MetricRegistryModule;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.ng.core.exceptionmappers.GenericExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.JerseyViolationExceptionMapperV2;
import io.harness.ng.core.exceptionmappers.NotFoundExceptionMapper;
import io.harness.ng.core.exceptionmappers.WingsExceptionMapperV2;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.remote.CharsetResponseFilter;
import io.harness.request.RequestContextFilter;
import io.harness.security.InternalApiAuthFilter;
import io.harness.security.NextGenAuthenticationFilter;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.PublicApi;
import io.harness.swagger.SwaggerBundleConfigurationFactory;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.filter.APIAuthTelemetryFilter;
import io.harness.telemetry.filter.APIAuthTelemetryResponseFilter;
import io.harness.telemetry.filter.TerraformTelemetryFilter;
import io.harness.token.remote.TokenClient;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.errors.EarlyEofExceptionMapper;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerBundle;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration.Dynamic;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@OwnedBy(PL)
@Slf4j
public class AccessControlApplication extends Application<AccessControlConfiguration> {
  private static final String APPLICATION_NAME = "Access Control Service";

  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.info("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new AccessControlApplication().run(args);
  }

  private final MetricRegistry metricRegistry = new MetricRegistry();

  @Override
  public String getName() {
    return APPLICATION_NAME;
  }

  @Override
  public void initialize(Bootstrap<AccessControlConfiguration> bootstrap) {
    initializeLogging();
    bootstrap.addCommand(new InspectCommand<>(this));
    bootstrap.addBundle(new SwaggerBundle<AccessControlConfiguration>() {
      @Override
      protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(AccessControlConfiguration appConfig) {
        return getSwaggerConfiguration(appConfig);
      }
    });
    bootstrap.addCommand(new ScanClasspathMetadataCommand());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand());
    bootstrap.setMetricRegistry(metricRegistry);
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    configureObjectMapperForNG(bootstrap.getObjectMapper());
  }

  @Override
  public void run(AccessControlConfiguration appConfig, Environment environment) {
    log.info("Starting Access Control Application ...");
    MaintenanceController.forceMaintenance(true);
    List<Module> modules = new ArrayList<>();
    modules.add(AccessControlModule.getInstance(appConfig));
    modules.add(new MetricRegistryModule(metricRegistry));
    modules.add(new ProviderModule() {
      @Provides
      @Singleton
      @Named("dbAliases")
      public List<String> getDbAliases() {
        return appConfig.getDbAliases();
      }
    });
    modules.add(new AbstractCfModule() {
      @Override
      public CfClientConfig cfClientConfig() {
        return appConfig.getCfClientConfig();
      }

      @Override
      public CfMigrationConfig cfMigrationConfig() {
        return CfMigrationConfig.builder().build();
      }

      @Override
      public FeatureFlagConfig featureFlagConfig() {
        return appConfig.getFeatureFlagConfig();
      }
    });
    Injector injector = Guice.createInjector(modules);
    injector.getInstance(HPersistence.class);
    registerCorsFilter(appConfig, environment);
    registerResources(environment, injector);
    registerJerseyProviders(environment);
    registerJerseyFeatures(environment);
    registerCharsetResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerRequestContextFilter(environment);
    registerAuthFilters(appConfig, environment, injector);
    registerAPIAuthTelemetryFilters(appConfig, environment, injector);
    registerHealthCheck(environment, injector);
    registerManagedBeans(appConfig, environment, injector);
    registerMigrations(injector);
    registerIterators(injector);
    registerOasResource(appConfig, environment, injector);

    if (BooleanUtils.isTrue(appConfig.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }

    initializeEnforcementFramework(injector);
    AccessControlManagementJob accessControlManagementJob = injector.getInstance(AccessControlManagementJob.class);
    accessControlManagementJob.run();

    if (appConfig.getAggregatorConfiguration().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(AggregatorService.class));
      environment.lifecycle().manage(injector.getInstance(MongoOffsetCleanupJob.class));
    }

    if (appConfig.getAggregatorConfiguration().isExportMetricsToStackDriver()) {
      initializeMonitoring(injector);
    }

    MaintenanceController.forceMaintenance(false);
  }

  private void initializeMonitoring(Injector injector) {
    injector.getInstance(MetricService.class).initializeMetrics();
    injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Access Control Service", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  public void registerIterators(Injector injector) {
    injector.getInstance(ResourceGroupReconciliationIterator.class).registerIterators();
    injector.getInstance(UserGroupReconciliationIterator.class).registerIterators();
    injector.getInstance(UserReconciliationIterator.class).registerIterators();
    injector.getInstance(ServiceAccountReconciliationIterator.class).registerIterators();
    injector.getInstance(SupportPreferenceReconciliationIterator.class).registerIterators();
    injector.getInstance(ScopeReconciliationIterator.class).registerIterators();
  }

  private void registerJerseyFeatures(Environment environment) {
    environment.jersey().register(MultiPartFeature.class);
  }

  private void registerOasResource(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    OpenApiResource openApiResource = injector.getInstance(OpenApiResource.class);
    openApiResource.setOpenApiConfiguration(configuration.getOasConfig());
    environment.jersey().register(openApiResource);
  }

  private void registerCorsFilter(AccessControlConfiguration appConfig, Environment environment) {
    Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    String allowedOrigins = String.join(",", appConfig.getAllowedOrigins());
    cors.setInitParameters(of("allowedOrigins", allowedOrigins, "allowedHeaders",
        "X-Requested-With,Content-Type,Accept,Origin,Authorization,X-api-key", "allowedMethods",
        "OPTIONS,GET,PUT,POST,DELETE,HEAD", "preflightMaxAge", "86400"));
    cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : ALL_ACCESS_CONTROL_RESOURCES) {
      environment.jersey().register(injector.getInstance(resource));
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerManagedBeans(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.getEventsConfig().isEnabled()) {
      environment.lifecycle().manage(injector.getInstance(EntityCrudEventListenerService.class));
      environment.lifecycle().manage(injector.getInstance(UserMembershipEventListenerService.class));
    }
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
    environment.lifecycle().manage(injector.getInstance(SupportRoleAssignmentsReconciliationService.class));
    environment.lifecycle().manage(injector.getInstance(UserRoleAssignmentRemovalService.class));
    environment.lifecycle().manage(injector.getInstance(ProjectOrgBasicRoleCreationService.class));
  }

  private void registerJerseyProviders(Environment environment) {
    environment.jersey().register(EarlyEofExceptionMapper.class);
    environment.jersey().register(ConstraintViolationExceptionMapper.class);
    environment.jersey().register(JerseyViolationExceptionMapperV2.class);
    environment.jersey().register(NGAccessDeniedExceptionMapper.class);
    environment.jersey().register(WingsExceptionMapperV2.class);
    environment.jersey().register(NotFoundExceptionMapper.class);
    environment.jersey().register(GenericExceptionMapperV2.class);
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
  }

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerRequestContextFilter(Environment environment) {
    environment.jersey().register(new RequestContextFilter());
  }

  private void registerAuthFilters(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.isAuthEnabled()) {
      registerAccessControlAuthFilter(configuration, environment, injector);
      registerInternalApiAuthFilter(configuration, environment);
    }
  }

  private void registerAPIAuthTelemetryFilters(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    if (configuration.getSegmentConfiguration() != null && configuration.getSegmentConfiguration().isEnabled()) {
      registerAPIAuthTelemetryFilter(environment, injector);
      registerTerraformTelemetryFilter(environment, injector);
      registerAPIAuthTelemetryResponseFilter(environment, injector);
    }
  }

  private void registerAPIAuthTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryFilter(telemetryReporter));
  }

  private void registerTerraformTelemetryFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new TerraformTelemetryFilter(telemetryReporter));
  }

  private void registerAPIAuthTelemetryResponseFilter(Environment environment, Injector injector) {
    TelemetryReporter telemetryReporter = injector.getInstance(TelemetryReporter.class);
    environment.jersey().register(new APIAuthTelemetryResponseFilter(telemetryReporter));
  }

  private Predicate<Pair<ResourceInfo, ContainerRequestContext>> getAuthenticationExemptedRequestsPredicate() {
    return getAuthFilterPredicate(PublicApi.class)
        .or(resourceInfoAndRequest
            -> resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/version")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.json")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith("/swagger.yaml")
                || resourceInfoAndRequest.getValue().getUriInfo().getAbsolutePath().getPath().endsWith(
                    "/openapi.json"));
  }

  private void registerAccessControlAuthFilter(
      AccessControlConfiguration configuration, Environment environment, Injector injector) {
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        (getAuthenticationExemptedRequestsPredicate().negate())
            .and((getAuthFilterPredicate(InternalApi.class)).negate());
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(BEARER.getServiceId(), configuration.getJwtAuthSecret());
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(MANAGER.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(NG_MANAGER.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(CI_MANAGER.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(CV_NEXT_GEN.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(DELEGATE_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(NOTIFICATION_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(PIPELINE_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(ACCESS_CONTROL_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(CODE.getServiceId(), configuration.getDefaultServiceSecret());
    serviceToSecretMapping.put(IDENTITY_SERVICE.getServiceId(), configuration.getIdentityServiceSecret());
    serviceToSecretMapping.put(IDP_SERVICE.getServiceId(), configuration.getDefaultServiceSecret());
    environment.jersey().register(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping,
        injector.getInstance(Key.get(TokenClient.class, Names.named("PRIVILEGED")))));
  }

  private void registerInternalApiAuthFilter(AccessControlConfiguration configuration, Environment environment) {
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    serviceToSecretMapping.put(DEFAULT.getServiceId(), configuration.getDefaultServiceSecret());
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

  private SwaggerBundleConfiguration getSwaggerConfiguration(AccessControlConfiguration appConfig) {
    Collection<Class<?>> classes = ALL_ACCESS_CONTROL_RESOURCES;
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration(classes);
    classes.add(AccessControlSwaggerListener.class);
    String resourcePackage = String.join(",", AccessControlConfiguration.getUniquePackages(classes));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setVersion("1.0");
    defaultSwaggerBundleConfiguration.setHost(appConfig.getHostname());
    defaultSwaggerBundleConfiguration.setUriPrefix(appConfig.getBasePathPrefix());
    defaultSwaggerBundleConfiguration.setTitle("Access Control Service API Reference");
    return defaultSwaggerBundleConfiguration;
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.ACCESSCONTROL) // this is only for locking purpose
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(AccessControlMigrationProvider.class); }
        })
        .build();
  }

  private void initializeEnforcementFramework(Injector injector) {
    Map<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>> featureRestrictionNameClassHashMap =
        ImmutableMap.<FeatureRestrictionName, Class<? extends RestrictionUsageInterface>>builder().build();
    RestrictionUsageRegisterConfiguration restrictionUsageRegisterConfiguration =
        RestrictionUsageRegisterConfiguration.builder()
            .restrictionNameClassMap(featureRestrictionNameClassHashMap)
            .build();
    CustomRestrictionRegisterConfiguration customConfig =
        CustomRestrictionRegisterConfiguration.builder()
            .customRestrictionMap(
                ImmutableMap.<FeatureRestrictionName, Class<? extends CustomRestrictionInterface>>builder().build())
            .build();
    injector.getInstance(EnforcementSdkRegisterService.class)
        .initialize(restrictionUsageRegisterConfiguration, customConfig);
  }
}
