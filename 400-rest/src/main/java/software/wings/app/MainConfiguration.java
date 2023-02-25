/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.Collections.singletonList;

import io.harness.AccessControlClientConfiguration;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cache.CacheConfig;
import io.harness.ccm.setup.config.CESetUpConfig;
import io.harness.cf.CfClientConfig;
import io.harness.cf.CfMigrationConfig;
import io.harness.commandlibrary.CommandLibraryServiceConfig;
import io.harness.config.DatadogConfig;
import io.harness.config.GcpMarketplaceConfig;
import io.harness.config.MockServerConfig;
import io.harness.config.PipelineConfig;
import io.harness.config.PublisherConfiguration;
import io.harness.config.WorkersConfiguration;
import io.harness.configuration.DeployMode;
import io.harness.configuration.DeployVariant;
import io.harness.delegate.beans.FileUploadLimit;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.event.handler.segment.SalesforceConfig;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.mongo.MongoConfig;
import io.harness.queueservice.config.DelegateQueueServiceConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.scheduler.SchedulerConfig;
import io.harness.secret.ConfigSecret;
import io.harness.secret.SecretsConfiguration;
import io.harness.stream.AtmosphereBroadcaster;
import io.harness.swagger.SwaggerBundleConfigurationFactory;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import software.wings.DataStorageMode;
import software.wings.audit.AuditConfig;
import software.wings.beans.DefaultSalesContacts;
import software.wings.beans.HttpMethod;
import software.wings.beans.UrlInfo;
import software.wings.beans.security.access.GlobalWhitelistConfig;
import software.wings.cdn.CdnConfig;
import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.jre.JreConfig;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.search.framework.ElasticsearchConfig;
import software.wings.search.redisConsumer.DebeziumConsumersConfig;
import software.wings.security.authentication.MarketPlaceConfig;
import software.wings.security.authentication.oauth.AzureConfig;
import software.wings.security.authentication.oauth.BitbucketConfig;
import software.wings.security.authentication.oauth.GithubConfig;
import software.wings.security.authentication.oauth.GitlabConfig;
import software.wings.security.authentication.oauth.GoogleConfig;
import software.wings.security.authentication.oauth.LinkedinConfig;
import software.wings.security.authentication.totp.TotpConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import io.dropwizard.bundles.assets.AssetsBundleConfiguration;
import io.dropwizard.bundles.assets.AssetsConfiguration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * Used to load all the application configuration.
 */
@TargetModule(HarnessModule._957_CG_BEANS)
@Data
@EqualsAndHashCode(callSuper = false)
@Singleton
@OwnedBy(PL)
public class MainConfiguration extends Configuration implements AssetsBundleConfiguration {
  @JsonProperty
  private AssetsConfiguration assetsConfiguration =
      AssetsConfiguration.builder()
          .mimeTypes(of("js", "application/json; charset=UTF-8", "zip", "application/zip"))
          .build();

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @JsonProperty("mongo") @ConfigSecret private MongoConfig mongoConnectionFactory = MongoConfig.builder().build();
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("events-mongo") @ConfigSecret private MongoConfig eventsMongo = MongoConfig.builder().uri("").build();
  @JsonProperty("dms-mongo") @ConfigSecret private MongoConfig dmsMongo = MongoConfig.builder().build();
  @JsonProperty("elasticsearch")
  private ElasticsearchConfig elasticsearchConfig = ElasticsearchConfig.builder().build();
  @JsonProperty(value = "searchEnabled") private boolean isSearchEnabled;
  @JsonProperty(value = "graphQLEnabled") private boolean isGraphQLEnabled;
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;
  @JsonProperty @ConfigSecret private PortalConfig portal = new PortalConfig();
  @JsonProperty("disableResourceValidation") private boolean disableResourceValidation;
  @JsonProperty("enableIterators") private boolean enableIterators;
  @JsonProperty("iteratorConfigPath") private String iteratorConfigPath;
  @JsonProperty(defaultValue = "true") private boolean enableAuth = true;
  @JsonProperty(defaultValue = "50") private int jenkinsBuildQuerySize = 50;
  @JsonProperty("iteratorsConfig") private IteratorsConfig iteratorsConfig;
  @JsonProperty("executorsConfig") private ExecutorsConfig executorsConfig;
  @JsonProperty("dataReconciliation") private DataReconciliationConfig dataReconciliationConfig;
  @JsonProperty private io.harness.delegate.beans.FileUploadLimit fileUploadLimits = new FileUploadLimit();
  @JsonProperty("backgroundScheduler") private SchedulerConfig backgroundSchedulerConfig = new SchedulerConfig();
  @JsonProperty("serviceScheduler") private SchedulerConfig serviceSchedulerConfig = new SchedulerConfig();
  @JsonProperty("watcherMetadataUrl") private String watcherMetadataUrl;
  @JsonProperty("delegateMetadataUrl") private String delegateMetadataUrl;
  @JsonProperty("agentMtlsSubdomain") private String agentMtlsSubdomain;
  @JsonProperty("awsInstanceTypes") private List<String> awsInstanceTypes;
  @JsonProperty("awsRegionIdToName") private Map<String, String> awsRegionIdToName;
  @JsonProperty("apiUrl") private String apiUrl;
  @JsonProperty("exportAccountDataBatchSize") private int exportAccountDataBatchSize;
  @JsonProperty("supportEmail") private String supportEmail;
  @JsonProperty("envPath") private String envPath;
  @JsonProperty("smtp") @ConfigSecret private SmtpConfig smtpConfig;
  @JsonProperty("globalWhitelistConfig") private GlobalWhitelistConfig globalWhitelistConfig;
  @JsonProperty(defaultValue = "KUBERNETES") private DeployMode deployMode = DeployMode.KUBERNETES;
  @JsonProperty(defaultValue = "SAAS") private DeployVariant deployVariant = DeployVariant.SAAS;
  @JsonProperty("featuresEnabled") private String featureNames;
  @JsonProperty("kubectlVersion") private String kubectlVersion;
  @JsonProperty("ocVersion") private String ocVersion;
  @JsonProperty("scmVersion") private String scmVersion;
  @JsonProperty("trialRegistrationAllowed") private boolean trialRegistrationAllowed;
  @JsonProperty("eventsFrameworkAvailableInOnPrem") private boolean eventsFrameworkAvailableInOnPrem;
  @JsonProperty(value = "trialRegistrationAllowedForBugathon", defaultValue = "false")
  private boolean trialRegistrationAllowedForBugathon;
  @JsonProperty("blacklistedEmailDomainsAllowed") private boolean blacklistedEmailDomainsAllowed;
  @JsonProperty("pwnedPasswordsAllowed") private boolean pwnedPasswordsAllowed;
  @JsonProperty("auditConfig") private AuditConfig auditConfig;
  @JsonProperty("executionLogStorageMode") private DataStorageMode executionLogsStorageMode;
  @JsonProperty("fileStorageMode") private DataStorageMode fileStorageMode;
  @JsonProperty("clusterName") private String clusterName;
  @JsonProperty("deploymentClusterName") private String deploymentClusterName;
  @JsonProperty("ceSetUpConfig") @ConfigSecret private CESetUpConfig ceSetUpConfig;
  @JsonProperty("marketoConfig") @ConfigSecret private MarketoConfig marketoConfig;
  @JsonProperty("segmentConfig") @ConfigSecret private SegmentConfig segmentConfig;
  @JsonProperty("segmentConfiguration") @ConfigSecret private SegmentConfiguration segmentConfiguration;
  @JsonProperty("salesforceConfig")
  @ConfigSecret
  private SalesforceConfig salesforceConfig = SalesforceConfig.builder().build();
  @JsonProperty("datadogConfig") @ConfigSecret private DatadogConfig datadogConfig;
  @JsonProperty("redisLockConfig") @ConfigSecret private RedisConfig redisLockConfig;
  @JsonProperty("redisDelegateConfig") @ConfigSecret private RedisConfig delegateServiceRedisConfig;
  @JsonProperty("redisAtmosphereConfig") @ConfigSecret private RedisConfig redisAtmosphereConfig;
  @JsonProperty("defaultSalesContacts") private DefaultSalesContacts defaultSalesContacts;
  @JsonProperty("githubConfig") private GithubConfig githubConfig;
  @JsonProperty("linkedinConfig") private LinkedinConfig linkedinConfig;
  @JsonProperty("googleConfig") private GoogleConfig googleConfig;
  @JsonProperty("azureConfig") private AzureConfig azureConfig;
  @JsonProperty("bitbucketConfig") private BitbucketConfig bitbucketConfig;
  @JsonProperty("gitlabConfig") private GitlabConfig gitlabConfig;
  @JsonProperty("mktPlaceConfig") @ConfigSecret private MarketPlaceConfig marketPlaceConfig;
  @JsonProperty("sampleTargetEnv") private String sampleTargetEnv;
  @JsonProperty("sampleTargetStatusHost") private String sampleTargetStatusHost;
  @JsonProperty("timescaledb") @ConfigSecret private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("ngAuthUIEnabled") private boolean ngAuthUIEnabled;
  @JsonProperty("gcpMarketplaceConfig") private GcpMarketplaceConfig gcpMarketplaceConfig;
  @JsonProperty("techStacks") private Map<String, UrlInfo> techStackLinks;
  @JsonProperty("grpcServerConfig") private GrpcServerConfig grpcServerConfig;
  @JsonProperty("grpcDelegateServiceClientConfig") private GrpcClientConfig grpcDelegateServiceClientConfig;
  @JsonProperty("grpcOnpremDelegateClientConfig") private GrpcClientConfig grpcOnpremDelegateClientConfig;
  @JsonProperty("grpcClientConfig") private GrpcClientConfig grpcClientConfig;
  @JsonProperty("grpcDMSClientConfig") private GrpcClientConfig grpcDMSClientConfig;
  @JsonProperty("workers") private WorkersConfiguration workers;
  @JsonProperty("publishers") private PublisherConfiguration publisherConfiguration;
  @JsonProperty("pipelineConfig") private PipelineConfig pipelineConfig = new PipelineConfig();
  @JsonProperty("currentJre") private String currentJre;
  @JsonProperty("migrateToJre") private String migrateToJre;
  @JsonProperty("jreConfigs") private Map<String, JreConfig> jreConfigs;
  @JsonProperty("cdnConfig") @ConfigSecret private CdnConfig cdnConfig;
  @JsonProperty("commandLibraryServiceConfig")
  @ConfigSecret
  private CommandLibraryServiceConfig commandLibraryServiceConfig = CommandLibraryServiceConfig.builder().build();
  @JsonProperty(value = "bugsnagApiKey") @ConfigSecret private String bugsnagApiKey;
  @JsonProperty("atmosphereBroadcaster") private AtmosphereBroadcaster atmosphereBroadcaster;
  @JsonProperty(value = "jobsFrequencyConfig") private JobsFrequencyConfig jobsFrequencyConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("mockServerConfig") private MockServerConfig mockServerConfig;
  @JsonProperty("numberOfRemindersBeforeAccountDeletion") private int numberOfRemindersBeforeAccountDeletion;
  @JsonProperty("delegateGrpcServicePort") private Integer delegateGrpcServicePort;
  @JsonProperty("logStreamingServiceConfig") @ConfigSecret private LogStreamingServiceConfig logStreamingServiceConfig;
  @JsonProperty("accessControlClient")
  @ConfigSecret
  private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("eventsFramework") @ConfigSecret private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("cfMigrationConfig") @ConfigSecret private CfMigrationConfig cfMigrationConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty("dmsSecret") @ConfigSecret private String dmsSecret;
  @JsonProperty(value = "disableDelegateMgmtInManager", defaultValue = "false")
  private boolean disableDelegateMgmtInManager;
  @JsonProperty("secretsConfiguration") private SecretsConfiguration secretsConfiguration;
  @JsonProperty("ldapSyncJobConfig") private LdapSyncJobConfig ldapSyncJobConfig;
  @JsonProperty("eventListenersCountConfig") private EventListenersCountConfig eventListenersCountConfig;
  @JsonProperty(value = "useGlobalKMSAsBaseAlgo", defaultValue = "false") private boolean useGlobalKMSAsBaseAlgo;
  @JsonProperty("totp") private TotpConfig totpConfig;
  @JsonProperty(value = "cdTsDbRetentionPeriodMonths") private String cdTsDbRetentionPeriodMonths;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty(value = "disableInstanceSyncIterator") private Boolean disableInstanceSyncIterator;
  @JsonProperty("delegateQueueServiceConfig") private DelegateQueueServiceConfig queueServiceConfig;
  @JsonProperty("debeziumConsumersConfigs") DebeziumConsumersConfig debeziumConsumerConfigs;
  // If this flag is enabled event framework is utilized for wait engine notification mechanism
  @JsonProperty(value = "redisNotifyEvent") private boolean redisNotifyEvent;

  // If flag is enabled, only one thread does Notify response cleanup.
  @JsonProperty(value = "lockNotifyResponseCleanup") private boolean lockNotifyResponseCleanup;
  @JsonProperty(value = "enableRedisForDelegateService", defaultValue = "false")
  private boolean enableRedisForDelegateService;

  private int applicationPort;
  private boolean sslEnabled;

  private static final String IS_OPTION_HEAD_HTTP_METHOD_BLOCKED = "IS_OPTION_HEAD_REQUEST_METHOD_BLOCKED";

  /**
   * Instantiates a new Main configuration.
   */
  public MainConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/api");
    defaultServerFactory.setRegisterDefaultExceptionMappers(false);
    defaultServerFactory.setAdminContextPath("/admin");
    defaultServerFactory.setAdminConnectors(singletonList(getDefaultAdminConnectorFactory()));
    defaultServerFactory.setApplicationConnectors(singletonList(getDefaultApplicationConnectorFactory()));
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
    defaultServerFactory.setAllowedMethods(getAllowedMethods());
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

  /**
   * Gets swagger bundle configuration.
   *
   * @return the swagger bundle configuration
   */
  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    Collection<Class<?>> resourceClasses = getResourceClasses();
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration(resourceClasses);
    defaultSwaggerBundleConfiguration.setResourcePackage(
        "software.wings.resources,software.wings.utils,io.harness.cvng.core.resources,io.harness.delegate.resources");
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost("{{host}}");
    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), "software.wings.resources",
                "software.wings.utils", "io.harness.cvng.core.resources", "io.harness.delegate.resources"))
        .collect(Collectors.toList());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AssetsConfiguration getAssetsConfiguration() {
    return assetsConfiguration;
  }

  protected ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(9091);
    return factory;
  }

  protected ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(9090);
    return factory;
  }

  private Set<String> getAllowedMethods() {
    if (System.getenv(IS_OPTION_HEAD_HTTP_METHOD_BLOCKED) != null
        && Boolean.parseBoolean(System.getenv(IS_OPTION_HEAD_HTTP_METHOD_BLOCKED))) {
      return new HashSet<>(Arrays.asList(HttpMethod.GET.name(), HttpMethod.PUT.name(), HttpMethod.POST.name(),
          HttpMethod.PATCH.name(), HttpMethod.DELETE.name()));
    }
    return new HashSet<>(Arrays.asList(HttpMethod.OPTIONS.name(), HttpMethod.POST.name(), HttpMethod.GET.name(),
        HttpMethod.PUT.name(), HttpMethod.HEAD.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name()));
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

  /**
   * Created by peeyushaggarwal on 8/30/16.
   */
  public abstract static class AssetsConfigurationMixin {
    /**
     * Gets resource path to uri mappings.
     *
     * @return the resource path to uri mappings
     */
    @JsonIgnore public abstract Map<String, String> getResourcePathToUriMappings();
  }

  @JsonIgnore
  public boolean useCdnForDelegateStorage() {
    return !DeployMode.isOnPrem(getDeployMode().name());
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConnectionFactory != null) {
      dbAliases.add(mongoConnectionFactory.getAliasDBName());
    }
    if (eventsMongo != null) {
      dbAliases.add(eventsMongo.getAliasDBName());
    }
    if (dmsMongo != null) {
      dbAliases.add(dmsMongo.getAliasDBName());
    }
    return dbAliases;
  }
}
