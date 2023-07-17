/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.accesscontrol.commons.events.EventsConfig;
import io.harness.accesscontrol.commons.iterators.AccessControlIteratorsConfig;
import io.harness.accesscontrol.commons.notifications.NotificationConfig;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountClientConfiguration;
import io.harness.accesscontrol.principals.usergroups.UserGroupClientConfiguration;
import io.harness.accesscontrol.principals.users.UserClientConfiguration;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupClientConfiguration;
import io.harness.accesscontrol.scopes.harness.AccountClientConfiguration;
import io.harness.accesscontrol.scopes.harness.OrganizationClientConfiguration;
import io.harness.accesscontrol.scopes.harness.ProjectClientConfiguration;
import io.harness.aggregator.AggregatorConfiguration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.CfClientConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.ff.FeatureFlagClientConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.telemetry.segment.SegmentConfiguration;

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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Getter
@Setter
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessControlConfiguration extends Configuration {
  public static final String SERVICE_ID = "access-control-microservice";
  public static final String PERMISSION_PACKAGE = "io.harness.accesscontrol.permissions.api";
  public static final String ROLES_PACKAGE = "io.harness.accesscontrol.roles.api";
  public static final String ROLE_ASSIGNMENTS_PACKAGE = "io.harness.accesscontrol.roleassignments.api";
  public static final String ACL_PACKAGE = "io.harness.accesscontrol.acl.api";
  public static final String ACCESSCONTROL_PREFERENCE_PACKAGE = "io.harness.accesscontrol.preference.api";
  public static final String AGGREGATOR_PACKAGE = "io.harness.accesscontrol.aggregator.api";
  public static final String ADMIN_PACKAGE = "io.harness.accesscontrol.admin.api";
  public static final String HEALTH_PACKAGE = "io.harness.accesscontrol.health";
  public static final String ENFORCEMENT_PACKAGE = "io.harness.enforcement.client.resources";
  public static final String ACCESSCONTROL_SERVER_STUB = "io.harness.spec.server.accesscontrol.v1";

  @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("allowedOrigins") private final List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("eventsConfig") private EventsConfig eventsConfig;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("iteratorsConfig") private AccessControlIteratorsConfig iteratorsConfig;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("resourceGroupClient") private ResourceGroupClientConfiguration resourceGroupClientConfiguration;
  @JsonProperty("userClient") private UserClientConfiguration userClientConfiguration;
  @JsonProperty("userGroupClient") private UserGroupClientConfiguration userGroupClientConfiguration;
  @JsonProperty("projectClient") private ProjectClientConfiguration projectClientConfiguration;
  @JsonProperty("organizationClient") private OrganizationClientConfiguration organizationClientConfiguration;
  @JsonProperty("ngManagerServiceConfiguration") private NgManagerServiceConfiguration ngManagerServiceConfiguration;
  @JsonProperty("accountClient") private AccountClientConfiguration accountClientConfiguration;
  @JsonProperty("notificationConfig") private NotificationConfig notificationConfig;
  @JsonProperty("aggregatorModuleConfig") private AggregatorConfiguration aggregatorConfiguration;
  @JsonProperty("enableAuth") @Getter(AccessLevel.NONE) private boolean enableAuth;
  @JsonProperty("defaultServiceSecret") private String defaultServiceSecret;
  @JsonProperty("jwtAuthSecret") private String jwtAuthSecret;
  @JsonProperty("identityServiceSecret") private String identityServiceSecret;
  @JsonProperty("enableAudit") private boolean enableAudit;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty("featureFlagClientConfiguration") private FeatureFlagClientConfiguration featureFlagClientConfiguration;
  @JsonProperty("outboxPollConfig") private OutboxPollConfiguration outboxPollConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("serviceAccountClient") private ServiceAccountClientConfiguration serviceAccountClientConfiguration;
  @JsonProperty("enforcementClientConfiguration") private EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("hostname") private String hostname = "localhost";
  @JsonProperty("basePathPrefix") private String basePathPrefix = "";
  @JsonProperty("segmentConfiguration") private SegmentConfiguration segmentConfiguration;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("disableRedundantACLs") private boolean disableRedundantACLs;
  @JsonProperty("enableParallelProcessingOfUserGroupUpdates")
  private boolean enableParallelProcessingOfUserGroupUpdates;

  public static final Collection<Class<?>> ALL_ACCESS_CONTROL_RESOURCES = getResourceClasses();

  public boolean isAuthEnabled() {
    return this.enableAuth;
  }

  public AccessControlConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/api");
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
    super.setServerFactory(defaultServerFactory);
  }

  private static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(clazz
            -> StringUtils.startsWithAny(clazz.getPackage().getName(), PERMISSION_PACKAGE, ROLES_PACKAGE,
                ROLE_ASSIGNMENTS_PACKAGE, ACL_PACKAGE, ACCESSCONTROL_PREFERENCE_PACKAGE, AGGREGATOR_PACKAGE,
                ADMIN_PACKAGE, HEALTH_PACKAGE, ENFORCEMENT_PACKAGE, ACCESSCONTROL_SERVER_STUB))
        .filter(clazz -> clazz.isInterface() || EmptyPredicate.isEmpty(clazz.getInterfaces()))
        .collect(Collectors.toSet());
  }

  private RequestLogFactory getDefaultlogbackAccessRequestLogFactory() {
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
            .title("Access Control API Reference")
            .description(
                "This is the Open Api Spec 3 for the Access Control Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("1.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    Collection<Class<?>> classes = ALL_ACCESS_CONTROL_RESOURCES;
    classes.add(AccessControlSwaggerListener.class);
    Set<String> packages = getUniquePackages(classes);
    return new SwaggerConfiguration().openAPI(oas).prettyPrint(true).resourcePackages(packages).scannerClass(
        "io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream()
        .filter(x -> x.isAnnotationPresent(Tag.class))
        .map(aClass -> aClass.getPackage().getName())
        .collect(toSet());
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}
