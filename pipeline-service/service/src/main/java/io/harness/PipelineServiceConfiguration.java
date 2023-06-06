/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.swagger.SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cf.CfClientConfig;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.event.OrchestrationLogConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.ngtriggers.TriggerConfiguration;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.opaclient.OpaServiceConfiguration;
import io.harness.pms.event.overviewLandingPage.DebeziumConsumersConfig;
import io.harness.pms.sdk.core.PipelineSdkRedisEventsConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.repositories.planExecutionJson.ExpandedJsonLockConfig;
import io.harness.secret.ConfigSecret;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.yaml.schema.client.config.YamlSchemaClientConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

// Todo: Streamline this
@OwnedBy(PIPELINE)
@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j
@Singleton
public class PipelineServiceConfiguration extends Configuration {
  public static final String RESOURCE_PACKAGE = "io.harness.pms";
  public static final String NG_TRIGGER_RESOURCE_PACKAGE = "io.harness.ngtriggers";
  public static final String FILTER_PACKAGE = "io.harness.filter";
  public static final String ENFORCEMENT_PACKAGE = "io.harness.enforcement";
  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;
  @JsonProperty("orchestrationVisualizationThreadPoolConfig")
  private ThreadPoolConfig orchestrationVisualizationThreadPoolConfig;
  @JsonProperty("pipelineExecutionPoolConfig") private ThreadPoolConfig pipelineExecutionPoolConfig;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("debeziumConsumersConfigs") DebeziumConsumersConfig debeziumConsumersConfigs;
  @JsonProperty("orchestrationPoolConfig") private ThreadPoolConfig orchestrationPoolConfig;
  @JsonProperty("grpcServerConfig") private GrpcServerConfig grpcServerConfig;
  @JsonProperty("grpcClientConfigs") private Map<String, GrpcClientConfig> grpcClientConfigs;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @JsonProperty("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;
  @JsonProperty("ciServiceClientConfig") private ServiceHttpClientConfig ciServiceClientConfig;
  @JsonProperty("containerStepConfigureWithCi") private boolean containerStepConfigureWithCi;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("pipelineServiceSecret") private String pipelineServiceSecret;
  @JsonProperty("templateServiceSecret") private String templateServiceSecret;
  @JsonProperty("ciServiceSecret") private String ciServiceSecret;
  @JsonProperty("jwtAuthSecret") private String jwtAuthSecret;
  @JsonProperty("jwtIdentityServiceSecret") private String jwtIdentityServiceSecret;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @Builder.Default @JsonProperty("allowedOrigins") private List<String> allowedOrigins = new ArrayList<>();
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("eventsFrameworkSnapshotDebezium")
  private EventsFrameworkConfiguration eventsFrameworkSnapshotConfiguration;
  @JsonProperty("pipelineServiceBaseUrl") private String pipelineServiceBaseUrl;
  @JsonProperty("pmsApiBaseUrl") private String pmsApiBaseUrl;
  @JsonProperty("yamlSchemaClientConfig") private YamlSchemaClientConfig yamlSchemaClientConfig;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("orchestrationStepConfig") private OrchestrationStepConfig orchestrationStepConfig;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("shouldUseEventsFrameworkSnapshotDebezium") private boolean shouldUseEventsFrameworkSnapshotDebezium;
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("segmentConfiguration") private SegmentConfiguration segmentConfiguration;
  @JsonProperty("pipelineEventConsumersConfig") PipelineServiceConsumersConfig pipelineServiceConsumersConfig;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("shouldUseInstanceCache") boolean shouldUseInstanceCache;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("planCreatorMergeServicePoolConfig") private ThreadPoolConfig planCreatorMergeServicePoolConfig;
  @JsonProperty("pipelineRedisEventsConfig") private PipelineRedisEventsConfig pipelineRedisEventsConfig;
  @JsonProperty("pipelineSdkRedisEventsConfig") private PipelineSdkRedisEventsConfig pipelineSdkRedisEventsConfig;
  @JsonProperty("orchestrationRedisEventsConfig") private OrchestrationRedisEventsConfig orchestrationRedisEventsConfig;
  @JsonProperty("allowedParallelStages") private int allowedParallelStages;
  @JsonProperty("orchestrationLogConfiguration") private OrchestrationLogConfiguration orchestrationLogConfiguration;
  @JsonProperty("planCreatorMergeServiceDependencyBatch") private Integer planCreatorMergeServiceDependencyBatch;
  @JsonProperty("jsonExpansionPoolConfig") private ThreadPoolConfig jsonExpansionPoolConfig;
  @JsonProperty("jsonExpansionRequestBatchSize") private Integer jsonExpansionBatchSize;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty(value = "orchestrationRestrictionConfiguration")
  OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;
  @JsonProperty("yamlSchemaExecutorServiceConfig") private ThreadPoolConfig yamlSchemaExecutorServiceConfig;
  @JsonProperty(value = "containerStepConfig") ContainerExecutionConfig containerExecutionConfig;
  @JsonProperty(value = "grpcNegotiationType") NegotiationType grpcNegotiationType;
  // If flag is enabled, only one thread does Notify response cleanup.
  @JsonProperty(value = "lockNotifyResponseCleanup") private boolean lockNotifyResponseCleanup;
  @JsonProperty("queueServiceClientConfig") private QueueServiceClientConfig queueServiceClientConfig;
  @JsonProperty(value = "disableFreezeNotificationTemplate") private boolean disableFreezeNotificationTemplate;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("triggerAuthenticationPoolConfig") private ThreadPoolConfig triggerAuthenticationPoolConfig;
  @JsonProperty("expandedJsonConfig") private ExpandedJsonLockConfig expandedJsonLockConfig;
  @JsonProperty("pipelineSetupUsageCreationExecutorServiceConfig")
  private ThreadPoolConfig pipelineSetupUsageCreationPoolConfig;

  @JsonProperty("podCleanUpThreadPoolConfig") private ThreadPoolConfig podCleanUpThreadPoolConfig;

  private String managerServiceSecret;
  private String managerTarget;
  private String managerAuthority;
  private ServiceHttpClientConfig managerClientConfig;
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  private TriggerConfiguration triggerConfig;
  private OpaServiceConfiguration opaServerConfig;
  private String policyManagerSecret;
  private ServiceHttpClientConfig opaClientConfig;

  private SSCAServiceConfig sscaServiceConfig;

  private PipelineServiceIteratorsConfig iteratorsConfig;
  private boolean shouldDeployWithGitSync;
  private GitSdkConfiguration gitSdkConfiguration;
  private DelegatePollingConfig delegatePollingConfig;
  private ThreadPoolConfig
      pipelineAsyncValidationPoolConfig; // to be used for defining thread config for async validations of Pipelines

  public PipelineServiceConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/api");
    defaultServerFactory.setRegisterDefaultExceptionMappers(Boolean.FALSE);
    defaultServerFactory.setAdminContextPath("/admin");
    defaultServerFactory.setAdminConnectors(singletonList(getDefaultAdminConnectorFactory()));
    defaultServerFactory.setApplicationConnectors(singletonList(getDefaultApplicationConnectorFactory()));
    defaultServerFactory.setRequestLogFactory(getDefaultLogbackAccessRequestLogFactory());
    defaultServerFactory.setMaxThreads(512);
    super.setServerFactory(defaultServerFactory);
  }

  @Override
  public void setServerFactory(ServerFactory factory) {
    DefaultServerFactory defaultServerFactory = (DefaultServerFactory) factory;
    ((DefaultServerFactory) getServerFactory())
        .setApplicationConnectors(defaultServerFactory.getApplicationConnectors());
    ((DefaultServerFactory) getServerFactory()).setAdminConnectors(defaultServerFactory.getAdminConnectors());
    ((DefaultServerFactory) getServerFactory()).setRequestLogFactory(defaultServerFactory.getRequestLogFactory());
    ((DefaultServerFactory) getServerFactory()).setMaxThreads(defaultServerFactory.getMaxThreads());
  }

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        buildSwaggerBundleConfiguration(HARNESS_RESOURCE_CLASSES);
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(hostname);
    defaultSwaggerBundleConfiguration.setUriPrefix(basePathPrefix);
    defaultSwaggerBundleConfiguration.setTitle("PMS API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");
    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), RESOURCE_PACKAGE, NG_TRIGGER_RESOURCE_PACKAGE,
                FILTER_PACKAGE, ENFORCEMENT_PACKAGE))
        .collect(Collectors.toSet());
  }

  private ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12001);
    return factory;
  }

  private ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12002);
    return factory;
  }

  private RequestLogFactory getDefaultLogbackAccessRequestLogFactory() {
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

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  public static Set<String> getOpenApiResources() {
    return HARNESS_RESOURCE_CLASSES.stream()
        .filter(x -> x.isAnnotationPresent(Tag.class))
        .map(Class::getName)
        .collect(toSet());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Pipeline Service API Reference")
            .description(
                "This is the Open Api Spec 3 for the Pipeline Service. This is under active development. Beware of the breaking change with respect to the generated code stub")
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
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    Set<String> resourceClasses = getOpenApiResources();
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}
