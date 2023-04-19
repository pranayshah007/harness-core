/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.swagger.SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration;

import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cf.CfClientConfig;
import io.harness.cvng.core.NGManagerServiceConfig;
import io.harness.cvng.core.WebhookConfig;
import io.harness.cvng.notification.config.ErrorTrackingClientConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.ManagerAuthConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.timescaledb.TimeScaleDBConfig;

import software.wings.app.PortalConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.dropwizard.Configuration;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;

@Data
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CV)
@Slf4j
public class VerificationConfiguration extends Configuration {
  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @JsonProperty("mongo") private MongoConfig mongoConnectionFactory = MongoConfig.builder().build();
  private ServiceHttpClientConfig managerClientConfig;
  private ServiceHttpClientConfig ticketServiceRestClientConfig;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerClientConfig;
  @JsonProperty("ngManagerServiceSecret") @ConfigSecret private String ngManagerServiceSecret;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  private ManagerAuthConfig managerAuthConfig;
  @JsonProperty("nextGen") private NGManagerServiceConfig ngManagerServiceConfig;
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("shouldConfigureWithNotification") private Boolean shouldConfigureWithNotification;
  @JsonProperty("cfClientConfig") private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("accessControlClientConfig") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("errorTrackingClientConfig") private ErrorTrackingClientConfig errorTrackingClientConfig;
  @JsonProperty("webhookConfig") private WebhookConfig webhookConfig;
  @JsonProperty("distributedLockImplementation")
  private DistributedLockImplementation distributedLockImplementation = DistributedLockImplementation.MONGO;
  private ServiceHttpClientConfig templateServiceClientConfig;
  private String templateServiceSecret;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty(value = "enableDebugAPI") private boolean enableDebugAPI;
  @JsonProperty @ConfigSecret private PortalConfig portal = new PortalConfig();
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  public static final String RESOURCE_PACKAGE = "io.harness.cvng";
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;

  private String portalUrl;

  private ServiceHttpClientConfig opaClientConfig;

  private String policyManagerSecret;

  /**
   * Instantiates a new Main configuration.
   */
  public VerificationConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath(SERVICE_BASE_URL);
    defaultServerFactory.setRegisterDefaultExceptionMappers(Boolean.FALSE);
    defaultServerFactory.setAdminContextPath("/admin");
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
    super.setServerFactory(defaultServerFactory);
  }

  @Override
  public void setServerFactory(ServerFactory factory) {
    DefaultServerFactory defaultServerFactory = (DefaultServerFactory) factory;
    ((DefaultServerFactory) getServerFactory())
        .setApplicationConnectors(defaultServerFactory.getApplicationConnectors());
    ((DefaultServerFactory) getServerFactory()).setAdminConnectors(defaultServerFactory.getAdminConnectors());
    ((DefaultServerFactory) getServerFactory()).setRequestLogFactory(defaultServerFactory.getRequestLogFactory());
  }

  /**
   * Gets swagger bundle configuration.
   *
   * @return the swagger bundle configuration
   */
  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    Set<String> resourcePackages = new HashSet<>();
    Set<Class<?>> reflections =
        HarnessReflections.get()
            .getTypesAnnotatedWith(Path.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(klazz.getPackage().getName(), this.getClass().getPackage().getName()))
            .collect(Collectors.toSet());
    reflections.forEach(resource -> {
      if (!resource.getPackage().getName().endsWith("resources")) {
        throw new IllegalStateException("Resource classes should be in resources package." + resource);
      }
      if (resource.isInterface() || EmptyPredicate.isEmpty(resource.getInterfaces())) {
        resourcePackages.add(resource.getPackage().getName());
      }
    });
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration = buildSwaggerBundleConfiguration(reflections);
    defaultSwaggerBundleConfiguration.setResourcePackage(String.join(",", resourcePackages));
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost("{{host}}");
    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  private RequestLogFactory getDefaultlogbackAccessRequestLogFactory() {
    LogbackAccessRequestLogFactory logbackAccessRequestLogFactory = new LogbackAccessRequestLogFactory();
    FileAppenderFactory<IAccessEvent> fileAppenderFactory = new FileAppenderFactory<>();
    fileAppenderFactory.setArchive(true);
    fileAppenderFactory.setCurrentLogFilename("cv-nextgen-access.log");
    fileAppenderFactory.setThreshold(Level.ALL.toString());
    fileAppenderFactory.setArchivedLogFilenamePattern("cv-nextgen-access.%d.log.gz");
    fileAppenderFactory.setArchivedFileCount(14);
    logbackAccessRequestLogFactory.setAppenders(ImmutableList.of(fileAppenderFactory));
    return logbackAccessRequestLogFactory;
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info = new Info()
                    .title("SRM Service API Reference")
                    .description("This is the Open Api Spec 3 for the SRM Service.")
                    .termsOfService("https://harness.io/terms-of-use/")
                    .version("3.0")
                    .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    try {
      URL baseurl = new URL("https", getHostname(), getBasePathPrefix());
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", getHostname(), getBasePathPrefix());
    }
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly().stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  private Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return getResourceClasses().stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  public static Collection<Class<?>> getResourceClasses() {
    Reflections reflections = new Reflections(RESOURCE_PACKAGE);
    return reflections.getTypesAnnotatedWith(Path.class);
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConnectionFactory != null) {
      dbAliases.add(mongoConnectionFactory.getAliasDBName());
    }
    return dbAliases;
  }
}
