/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.config.AiEngineConfig;
import io.harness.ccm.commons.beans.config.AwsConfig;
import io.harness.ccm.commons.beans.config.AwsGovCloudConfig;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.beans.config.GcpConfig;
import io.harness.ccm.config.CurrencyPreferencesConfig;
import io.harness.cf.CfClientConfig;
import io.harness.configuration.DeployMode;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.remote.CEAzureSetupConfig;
import io.harness.remote.CEGcpSetupConfig;
import io.harness.remote.GovernanceConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.secret.SecretsConfiguration;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.timescaledb.TimeScaleDBConfig;

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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Getter
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CE)
public class CENextGenConfiguration extends Configuration {
  public static final String SERVICE_ROOT_PATH = "/ccm/api";
  public static final String SERVICE_ID = "cenextgen-microservice";
  public static final String BASE_PACKAGE = "io.harness.ccm";
  public static final String FILTER_PACKAGE = "io.harness.filter";

  public static final List<String> RESOURCE_PACKAGES = ImmutableList.of("io.harness.ccm.remote.resources");
  public static final String LICENSE_PACKAGE = "io.harness.licensing.usage.resources";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @Setter @JsonProperty("events-mongo") @ConfigSecret private MongoConfig eventsMongoConfig;
  @JsonProperty("allowedOrigins") private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerClientConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("grpcClient") private GrpcClientConfig grpcClientConfig;
  @JsonProperty(value = "enableAuth", defaultValue = "false") private boolean enableAuth;
  @JsonProperty("ngManagerServiceSecret") @ConfigSecret private String ngManagerServiceSecret;
  @JsonProperty("jwtAuthSecret") @ConfigSecret private String jwtAuthSecret;
  @JsonProperty("jwtIdentityServiceSecret") @ConfigSecret private String jwtIdentityServiceSecret;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("accessControlClient")
  @ConfigSecret
  private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("accessControlAdminClient")
  @ConfigSecret
  private AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;

  @JsonProperty(defaultValue = "KUBERNETES") private DeployMode deployMode = DeployMode.KUBERNETES;
  @JsonProperty(value = "featureFlagsEnabled", defaultValue = "") private String featureFlagsEnabled;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("eventsFramework") @ConfigSecret private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("timescaledb") @ConfigSecret private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("clickHouseConfig") @ConfigSecret private ClickHouseConfig clickHouseConfig;
  @JsonProperty("isClickHouseEnabled") private boolean isClickHouseEnabled;

  @JsonProperty(value = "gcpConfig") private GcpConfig gcpConfig;
  @JsonProperty(value = "ceAzureSetupConfig") @ConfigSecret private CEAzureSetupConfig ceAzureSetupConfig;
  @JsonProperty(value = "ceGcpSetupConfig") @ConfigSecret private CEGcpSetupConfig ceGcpSetupConfig;
  @JsonProperty(value = "awsConfig") @ConfigSecret private AwsConfig awsConfig;
  @JsonProperty(value = "awsGovCloudConfig") @ConfigSecret private AwsGovCloudConfig awsGovCloudConfig;
  @JsonProperty(value = "awsGovCloudCftUrl") @ConfigSecret private String awsGovCloudCftUrl;

  @JsonProperty("segmentConfiguration") @ConfigSecret private SegmentConfiguration segmentConfiguration;

  @JsonProperty("deploymentClusterName") private String deploymentClusterName;

  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty("exportMetricsToStackDriver") private boolean exportMetricsToStackDriver;
  @JsonProperty("outboxPollConfig") private OutboxPollConfiguration outboxPollConfig;

  @JsonProperty(value = "hostname") private String hostname = "localhost";
  @JsonProperty(value = "basePathPrefix") private String basePathPrefix = "";
  @JsonProperty(value = "awsConnectorCreatedInstantForPolicyCheck")
  private String awsConnectorCreatedInstantForPolicyCheck;

  @JsonProperty("secretsConfiguration") private SecretsConfiguration secretsConfiguration;
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;

  @JsonProperty("lightwingAutoCUDClientConfig") private ServiceHttpClientConfig lightwingAutoCUDClientConfig;
  @JsonProperty("dkronClientConfig") private ServiceHttpClientConfig dkronClientConfig;
  @JsonProperty("governanceConfig") private GovernanceConfig governanceConfig;
  @JsonProperty(value = "enableLightwingAutoCUDDC") private boolean enableLightwingAutoCUDDC;

  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty(value = "currencyPreferences") private CurrencyPreferencesConfig currencyPreferencesConfig;
  @JsonProperty("aiEngineConfig") private AiEngineConfig aiEngineConfig;
  @JsonProperty("redisLockConfig") @ConfigSecret private RedisConfig redisLockConfig;

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerConf = new SwaggerBundleConfiguration();

    String resourcePackage = String.join(",", getUniquePackages(getResourceClasses()));
    defaultSwaggerConf.setResourcePackage(resourcePackage);
    defaultSwaggerConf.setSchemes(new String[] {"https", "http"});
    defaultSwaggerConf.setTitle("CE NextGen API Reference");
    defaultSwaggerConf.setVersion("1.0");

    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerConf);
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  public static Collection<Class<?>> getResourceClasses() {
    final Reflections reflections =
        new Reflections(RESOURCE_PACKAGES, LICENSE_PACKAGE, ENFORCEMENT_CLIENT_PACKAGE, FILTER_PACKAGE);

    return reflections.getTypesAnnotatedWith(Path.class);
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return getResourceClasses().stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  public CENextGenConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath(SERVICE_ROOT_PATH);
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
    super.setServerFactory(defaultServerFactory);
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
            .title("CCM NextGen API Reference")
            .description(
                "This is the Open Api Spec 3 for the CCM NextGen Manager. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);

    List<Server> serversList = new ArrayList<>();

    try {
      URL baseurl = new URL("https", hostname, basePathPrefix);
      serversList.add(new Server().url(baseurl.toString()));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }

    oas.servers(serversList);

    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly().stream().map(Class::getCanonicalName).collect(toSet());

    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (eventsMongoConfig != null) {
      dbAliases.add(eventsMongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}
