/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.resourcegroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.platform.PlatformConfiguration.RESOURCE_GROUP_RESOURCES;

import static java.util.stream.Collectors.toSet;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.controller.PrimaryVersionChangeScheduler;
import io.harness.enforcement.client.CustomRestrictionRegisterConfiguration;
import io.harness.enforcement.client.RestrictionUsageRegisterConfiguration;
import io.harness.enforcement.client.custom.CustomRestrictionInterface;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.health.HealthService;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigrationSdkInitHelper;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.ng.core.CorrelationFilter;
import io.harness.outbox.OutboxEventPollService;
import io.harness.persistence.HPersistence;
import io.harness.platform.remote.ResourceGroupOpenApiResource;
import io.harness.remote.CharsetResponseFilter;
import io.harness.resource.VersionInfoResource;
import io.harness.resourcegroup.ResourceGroupServiceConfig;
import io.harness.resourcegroup.ResourceGroupsManagementJob;
import io.harness.resourcegroup.migrations.ResourceGroupMigrationProvider;
import io.harness.resourcegroup.reconciliation.ResourceGroupAsyncReconciliationHandler;
import io.harness.resourcegroup.reconciliation.ResourceGroupSyncConciliationService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import io.dropwizard.setup.Environment;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(PL)
public class ResourceGroupServiceSetup {
  public static final String RESOURCE_GROUP_SERVICE = "ResourceGroupService";

  public ResourceGroupServiceSetup() {
    // sonar
  }

  public void setup(ResourceGroupServiceConfig appConfig, Environment environment, Injector injector) {
    // Will create collections and Indexes
    injector.getInstance(HPersistence.class);
    registerResources(environment, injector);
    registerCharsetResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerIterators(injector);
    registerScheduledJobs(injector);
    registerManagedBeans(environment, injector);
    registerMigrations(injector);
    registerHealthCheck(environment, injector);
    initializeMonitoring(appConfig, injector);
    initializeEnforcementFramework(injector);
    ResourceGroupsManagementJob resourceGroupsManagementJob = injector.getInstance(ResourceGroupsManagementJob.class);
    resourceGroupsManagementJob.run();
    registerOasResource(appConfig, environment, injector);
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("ResourceGroup Application", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerIterators(Injector injector) {
    injector.getInstance(ResourceGroupAsyncReconciliationHandler.class).registerIterators();
  }

  private void initializeMonitoring(ResourceGroupServiceConfig appConfig, Injector injector) {
    if (appConfig.isExportMetricsToStackDriver()) {
      injector.getInstance(MetricService.class).initializeMetrics();
      injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
    }
  }

  private void registerScheduledJobs(Injector injector) {
    injector.getInstance(PrimaryVersionChangeScheduler.class).registerExecutors();
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(ResourceGroupSyncConciliationService.class));
    environment.lifecycle().manage(injector.getInstance(OutboxEventPollService.class));
  }

  private void registerMigrations(Injector injector) {
    NGMigrationConfiguration config = getMigrationSdkConfiguration();
    NGMigrationSdkInitHelper.initialize(injector, config);
  }

  private NGMigrationConfiguration getMigrationSdkConfiguration() {
    return NGMigrationConfiguration.builder()
        .microservice(Microservice.RESOURCEGROUP)
        .migrationProviderList(new ArrayList<Class<? extends MigrationProvider>>() {
          { add(ResourceGroupMigrationProvider.class); }
        })
        .build();
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : RESOURCE_GROUP_RESOURCES) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }

  private void registerCharsetResponseFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CharsetResponseFilter.class));
  }

  private void registerCorrelationFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(CorrelationFilter.class));
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

  private void registerOasResource(ResourceGroupServiceConfig appConfig, Environment environment, Injector injector) {
    ResourceGroupOpenApiResource resourceGroupOpenApiResource =
        injector.getInstance(ResourceGroupOpenApiResource.class);
    resourceGroupOpenApiResource.setOpenApiConfiguration(getOasConfig(appConfig));
    resourceGroupOpenApiResource.setModule(RESOURCE_GROUP_SERVICE);
    environment.jersey().register(resourceGroupOpenApiResource);
  }

  private OpenAPIConfiguration getOasConfig(ResourceGroupServiceConfig appConfig) {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Resource Group Service API Reference")
            .description(
                "This is the Open Api Spec 3 for the Resource Group Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", appConfig.getHostname(), appConfig.getBasePathPrefix());
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error(
          "The base URL of the server could not be set. {}/{}", appConfig.getHostname(), appConfig.getBasePathPrefix());
    }
    final Set<String> resourceClasses = RESOURCE_GROUP_RESOURCES.stream()
                                            .filter(x -> x.isAnnotationPresent(Tag.class))
                                            .map(Class::getCanonicalName)
                                            .collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .cacheTTL(0L)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }
}
