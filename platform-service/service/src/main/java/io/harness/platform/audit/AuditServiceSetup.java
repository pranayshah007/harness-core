/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.platform.PlatformConfiguration.AUDIT_SERVICE_RESOURCES;

import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.retention.AuditAccountSyncService;
import io.harness.audit.retention.AuditRetentionIteratorHandler;
import io.harness.health.HealthService;
import io.harness.metrics.jobs.RecordMetricsJob;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.CorrelationFilter;
import io.harness.ng.core.TraceFilter;
import io.harness.persistence.HPersistence;
import io.harness.platform.remote.AuditOpenApiResource;
import io.harness.platform.remote.VersionInfoResource;
import io.harness.remote.CharsetResponseFilter;

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
import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(PL)
public class AuditServiceSetup {
  public static final String AUDIT_SERVICE = "AuditService";

  public AuditServiceSetup() {
    // sonar
  }

  public void setup(AuditServiceConfiguration appConfig, Environment environment, Injector injector) {
    // Will create collections and Indexes
    injector.getInstance(HPersistence.class);
    registerResources(environment, injector);
    registerCharsetResponseFilter(environment, injector);
    registerCorrelationFilter(environment, injector);
    registerHealthCheck(environment, injector);
    registerManagedBeans(environment, injector);
    registerIterators(injector);
    registerOasResource(appConfig, environment, injector);
    initializeMonitoring(appConfig, injector);

    if (BooleanUtils.isTrue(appConfig.getEnableOpentelemetry())) {
      registerTraceFilter(environment, injector);
    }
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Audit Application", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : AUDIT_SERVICE_RESOURCES) {
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

  private void registerTraceFilter(Environment environment, Injector injector) {
    environment.jersey().register(injector.getInstance(TraceFilter.class));
  }

  private void registerManagedBeans(Environment environment, Injector injector) {
    environment.lifecycle().manage(injector.getInstance(AuditAccountSyncService.class));
  }

  private void registerIterators(Injector injector) {
    injector.getInstance(AuditRetentionIteratorHandler.class).registerIterators();
  }

  private void registerOasResource(AuditServiceConfiguration appConfig, Environment environment, Injector injector) {
    AuditOpenApiResource auditOpenApiResource = injector.getInstance(AuditOpenApiResource.class);
    auditOpenApiResource.setOpenApiConfiguration(getOasConfig(appConfig));
    auditOpenApiResource.setModule(AUDIT_SERVICE);
    environment.jersey().register(auditOpenApiResource);
  }

  private OpenAPIConfiguration getOasConfig(AuditServiceConfiguration appConfig) {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Audit Service API Reference")
            .description(
                "This is the Open Api Spec 3 for the Audit Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
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
    final Set<String> resourceClasses = AUDIT_SERVICE_RESOURCES.stream()
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

  private void initializeMonitoring(AuditServiceConfiguration appConfig, Injector injector) {
    if (appConfig.isExportMetricsToStackDriver()) {
      injector.getInstance(MetricService.class).initializeMetrics();
      injector.getInstance(RecordMetricsJob.class).scheduleMetricsTasks();
    }
  }
}
