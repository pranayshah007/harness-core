/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.platform.audit.AuditServiceConfiguration;
import io.harness.platform.notification.NotificationServiceConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedisReadMode;
import io.harness.redis.RedisSSLConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroup.ResourceGroupServiceConfig;
import io.harness.resourcegroup.v1.remote.dto.ZendeskConfig;
import io.harness.secret.ConfigSecret;
import io.harness.secret.SecretsConfiguration;
import io.harness.threading.ThreadPoolConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.dropwizard.Configuration;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
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
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Getter
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(PL)
public class PlatformConfiguration extends Configuration {
  public static final String SERVICE_ID = "platform-microservice";
  public static final String BASE_PACKAGE = "io.harness.platform";
  public static final String PLATFORM_RESOURCE_PACKAGE = "io.harness.platform.remote";
  public static final String NOTIFICATION_RESOURCE_PACKAGE = "io.harness.notification.remote.resources";
  public static final String AUDIT_RESOURCE_PACKAGE = "io.harness.audit.remote";
  public static final String FILTER_RESOURCE_PACKAGE = "io.harness.filter";
  public static final String RESOURCEGROUP_PACKAGE = "io.harness.resourcegroup";
  public static final String ENFORCEMENT_PACKAGE = "io.harness.enforcement.client.resources";
  RedisSSLConfig redisSSLConfig =
      RedisSSLConfig.builder().CATrustStorePassword("").CATrustStorePath("").enabled(false).build();
  RedisConfig redisConfig = RedisConfig.builder()
                                .redisUrl("dummyRedisUrl")
                                .masterName("test")
                                .sentinel(false)
                                .sentinelUrls(Collections.singletonList(""))
                                .envNamespace("")
                                .readMode(RedisReadMode.MASTER)
                                .nettyThreads(16)
                                .userName("")
                                .password("")
                                .sslConfig(redisSSLConfig)
                                .build();
  @Setter
  @JsonProperty("notificationServiceConfig")
  @ConfigSecret
  private NotificationServiceConfiguration notificationServiceConfig;
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;
  @JsonProperty("auditServiceConfig")
  @ConfigSecret
  private AuditServiceConfiguration auditServiceConfig =
      AuditServiceConfiguration.builder().hostname("localhost").basePathPrefix("").build();
  @JsonProperty("resourceGroupServiceConfig")
  @ConfigSecret
  private ResourceGroupServiceConfig resoureGroupServiceConfig =
      ResourceGroupServiceConfig.builder().hostname("localhost").basePathPrefix("").enableResourceGroup(true).build();

  @JsonProperty("allowedOrigins") private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerServiceConfig;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerServiceConfig;
  @JsonProperty("rbacServiceConfig") private ServiceHttpClientConfig rbacServiceConfig;
  @JsonProperty("zendeskApiConfig") private ZendeskConfig zendeskConfig;
  @JsonProperty("secrets") @ConfigSecret private PlatformSecrets platformSecrets;
  @JsonProperty(value = "enableAuth", defaultValue = "true") private boolean enableAuth;
  @JsonProperty(value = "environment", defaultValue = "dev") private String environment;
  @JsonProperty(value = "accessControlClient")
  @ConfigSecret
  private AccessControlClientConfiguration accessControlClientConfig;
  @JsonProperty("enforcementClientConfiguration") private EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("secretsConfiguration") private SecretsConfiguration secretsConfiguration;
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("eventsFramework")
  @ConfigSecret
  private EventsFrameworkConfiguration eventsFrameworkConfiguration =
      EventsFrameworkConfiguration.builder().redisConfig(redisConfig).build();

  public static final Collection<Class<?>> ALL_HARNESS_RESOURCES = getAllResources();
  public static final Collection<Class<?>> NOTIFICATION_SERVICE_RESOURCES = getNotificationServiceResourceClasses();
  public static final Collection<Class<?>> AUDIT_SERVICE_RESOURCES = getAuditServiceResourceClasses();
  public static final Collection<Class<?>> RESOURCE_GROUP_RESOURCES = getResourceGroupServiceResourceClasses();

  private static Collection<Class<?>> getAllResources() {
    return HarnessReflections.get().getTypesAnnotatedWith(Path.class);
  }

  private static Collection<Class<?>> getNotificationServiceResourceClasses() {
    return ALL_HARNESS_RESOURCES.stream()
        .filter(clazz -> StringUtils.startsWithAny(clazz.getPackage().getName(), NOTIFICATION_RESOURCE_PACKAGE))
        .collect(Collectors.toSet());
  }

  private static Collection<Class<?>> getAuditServiceResourceClasses() {
    return ALL_HARNESS_RESOURCES.stream()
        .filter(clazz -> StringUtils.startsWithAny(clazz.getPackage().getName(), AUDIT_RESOURCE_PACKAGE))
        .collect(Collectors.toSet());
  }

  private static Collection<Class<?>> getResourceGroupServiceResourceClasses() {
    return ALL_HARNESS_RESOURCES.stream()
        .filter(clazz -> StringUtils.startsWithAny(clazz.getPackage().getName(), RESOURCEGROUP_PACKAGE))
        .collect(Collectors.toSet());
  }

  public static Collection<Class<?>> getPlatformServiceCombinedResourceClasses(PlatformConfiguration appConfig) {
    Collection<Class<?>> resources = new HashSet<>(NOTIFICATION_SERVICE_RESOURCES);
    if (appConfig.getAuditServiceConfig().isEnableAuditService()) {
      resources.addAll(AUDIT_SERVICE_RESOURCES);
    }
    if (appConfig.getResoureGroupServiceConfig().isEnableResourceGroup()) {
      resources.addAll(RESOURCE_GROUP_RESOURCES);
    }
    resources.addAll(ALL_HARNESS_RESOURCES.stream()
                         .filter(clazz -> StringUtils.startsWithAny(clazz.getPackage().getName(), ENFORCEMENT_PACKAGE))
                         .collect(Collectors.toSet()));
    return resources.stream()
        .filter(clazz -> clazz.isInterface() || EmptyPredicate.isEmpty(clazz.getInterfaces()))
        .collect(toSet());
  }

  public PlatformConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/api");
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
    super.setServerFactory(defaultServerFactory);
  }

  private RequestLogFactory<?> getDefaultlogbackAccessRequestLogFactory() {
    LogbackAccessRequestLogFactory logbackAccessRequestLogFactory = new LogbackAccessRequestLogFactory();
    FileAppenderFactory<IAccessEvent> fileAppenderFactory = new FileAppenderFactory<>();
    fileAppenderFactory.setArchive(true);
    fileAppenderFactory.setCurrentLogFilename("access.log");
    fileAppenderFactory.setThreshold(Level.ALL.toString());
    fileAppenderFactory.setArchivedLogFilenamePattern("access.%d.log.gz");
    fileAppenderFactory.setArchivedFileCount(14);
    logbackAccessRequestLogFactory.setAppenders(ImmutableList.of(fileAppenderFactory));
    return logbackAccessRequestLogFactory;
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Platform Service API Reference")
            .description(
                "This is the Open Api Spec 3 for the Platform Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("The base URL of the server could not be set. {}/{}", hostname, basePathPrefix);
    }
    Collection<Class<?>> allResourceClasses = getPlatformServiceCombinedResourceClasses(this);
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly(allResourceClasses).stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly(Collection<Class<?>> allResourceClasses) {
    return allResourceClasses.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (notificationServiceConfig != null && notificationServiceConfig.getMongoConfig() != null) {
      dbAliases.add(notificationServiceConfig.getMongoConfig().getAliasDBName());
    }
    if (auditServiceConfig != null && auditServiceConfig.getMongoConfig() != null) {
      dbAliases.add(auditServiceConfig.getMongoConfig().getAliasDBName());
    }
    if (resoureGroupServiceConfig != null && resoureGroupServiceConfig.getMongoConfig() != null) {
      dbAliases.add(resoureGroupServiceConfig.getMongoConfig().getAliasDBName());
    }
    return dbAliases;
  }
}
