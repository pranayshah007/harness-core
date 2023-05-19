/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.swagger.SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration;

import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.Microservice;
import io.harness.NgIteratorsConfig;
import io.harness.accesscontrol.AccessControlAdminClientConfiguration;
import io.harness.account.AccountConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.cdng.plugininfoproviders.PluginExecutionConfig;
import io.harness.cf.CfClientConfig;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.file.FileServiceConfiguration;
import io.harness.gitops.GitopsResourceClientConfig;
import io.harness.gitsync.GitSdkConfiguration;
import io.harness.gitsync.GitServiceConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.opaclient.OpaServiceConfiguration;
import io.harness.outbox.OutboxPollConfiguration;
import io.harness.pms.redisConsumer.DebeziumConsumersConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.CEAwsSetupConfig;
import io.harness.remote.CEAzureSetupConfig;
import io.harness.remote.CEGcpSetupConfig;
import io.harness.remote.NextGenConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.resourcegroupclient.remote.ResourceGroupClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.secret.SecretsConfiguration;
import io.harness.signup.SignupDomainDenylistConfiguration;
import io.harness.signup.SignupNotificationConfiguration;
import io.harness.subscription.SubscriptionConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import software.wings.security.authentication.oauth.BitbucketConfig;
import software.wings.security.authentication.oauth.GitlabConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import io.dropwizard.Configuration;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NextGenConfiguration extends Configuration {
  public static final String SERVICE_ID = "ng-manager";
  public static final String CORE_PACKAGE = "io.harness.ng.core.remote";
  public static final String INVITE_PACKAGE = "io.harness.ng.core.invites.remote";
  public static final String CONNECTOR_PACKAGE = "io.harness.connector.apis.resource";
  public static final String GITOPS_PROVIDER_RESOURCE_PACKAGE = "io.harness.gitopsprovider.resource";
  public static final String GIT_SYNC_PACKAGE = "io.harness.gitsync";
  public static final String CDNG_RESOURCES_PACKAGE = "io.harness.cdng";
  public static final String OVERLAY_INPUT_SET_RESOURCE_PACKAGE = "io.harness.ngpipeline";
  public static final String YAML_PACKAGE = "io.harness.yaml";
  public static final String FILTER_PACKAGE = "io.harness.filter";
  public static final String SIGNUP_PACKAGE = "io.harness.signup";
  public static final String MOCKSERVER_PACKAGE = "io.harness.ng.core.acl.mockserver";
  public static final String ACCOUNT_PACKAGE = "io.harness.account.resource";
  public static final String LICENSE_PACKAGE = "io.harness.licensing.api.resource";
  public static final String SUBSCRIPTION_PACKAGE = "io.harness.subscription.resource";
  public static final String CREDIT_PACKAGE = "io.harness.credit.resource";
  public static final String POLLING_PACKAGE = "io.harness.polling.resource";
  public static final String ENFORCEMENT_PACKAGE = "io.harness.enforcement.resource";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";
  public static final String ARTIFACTS_PACKAGE = "io.harness.ng.core.artifacts.resources";
  public static final String AUTHENTICATION_SETTINGS_PACKAGE = "io.harness.ng.authenticationsettings.resources";
  public static final String SERVICE_PACKAGE = "io.harness.ng.core.service.resources";
  public static final String CUSTOM_DEPLOYMENT_PACKAGE = "io.harness.ng.core.customDeployment.resources";
  public static final String TAS_PACKAGE = "io.harness.ng.core.tas.resources";
  public static final String VARIABLE_RESOURCE_PACKAGE = "io.harness.ng.core.variable.resources";
  public static final String CD_OVERVIEW_PACKAGE = "io.harness.ng.overview.resource";
  public static final String ROLLBACK_PACKAGE = "io.harness.ng.rollback";
  public static final String ACTIVITY_HISTORY_PACKAGE = "io.harness.ng.core.activityhistory.resource";
  public static final String SERVICE_ACCOUNTS_PACKAGE = "io.harness.ng.serviceaccounts.resource";
  public static final String BUCKETS_PACKAGE = "io.harness.ng.core.buckets.resources";
  public static final String CLUSTER_GCP_PACKAGE = "io.harness.ng.core.k8s.cluster.resources.gcp";
  public static final String WEBHOOK_PACKAGE = "io.harness.ng.webhook.resources";
  public static final String ENVIRONMENT_PACKAGE = "io.harness.ng.core.environment.resources";
  public static final String SERVICE_OVERRIDES_PACKAGE = "io.harness.ng.core.serviceoverrides.resources";
  public static final String USERPROFILE_PACKAGE = "io.harness.ng.userprofile.resource";
  public static final String USER_PACKAGE = "io.harness.ng.core.user.remote";
  public static final String JIRA_PACKAGE = "io.harness.ng.jira.resources";
  public static final String EXECUTION_PACKAGE = "io.harness.ng.executions.resources";
  public static final String ENTITYSETUP_PACKAGE = "io.harness.ng.core.entitysetupusage.resource";
  public static final String SCHEMA_PACKAGE = "io.harness.ng.core.schema.resource";
  public static final String DELEGATE_PACKAGE = "io.harness.ng.core.delegate.resources";
  public static final String AGENT_PACKAGE = "io.harness.ng.core.agent.resources";
  public static final String ACCESS_CONTROL_PACKAGE = "io.harness.ng.accesscontrol.resources";
  public static final String FEEDBACK_PACKAGE = "io.harness.ng.feedback.resources";
  public static final String INSTANCE_SYNC_PACKAGE = "io.harness.ng.instancesync.resources";
  public static final String INSTANCE_NG_PACKAGE = "io.harness.ng.instance";
  public static final String SMTP_NG_RESOURCE = "io.harness.ng.core.smtp.resources";
  public static final String SERVICENOW_PACKAGE = "io.harness.ng.servicenow.resources";
  public static final String SCIM_NG_RESOURCE = "io.harness.ng.scim.resource";
  public static final String LICENSING_USAGE_PACKAGE = "io.harness.licensing.usage.resources";
  public static final String ACCOUNT_SETTING_PACKAGE = "io.harness.ng.core.accountsetting.resources";
  public static final String ENV_GROUP_RESOURCE = "io.harness.ng.core.envGroup.resource";
  public static final String NG_GLOBAL_KMS_RESOURCE_PACKAGE = "io.harness.ng.core.globalkms.resource";
  public static final String AZURE_RESOURCES_PACKAGE = "io.harness.ng.core.resources.azure";
  public static final String NG_TRIAL_SIGNUP_PACKAGE = "io.harness.ng.trialsignup";
  public static final String AWS_PACKAGE = "io.harness.ng.core.aws.resources";
  public static final String FILE_STORE_RESOURCE_PACKAGE = "io.harness.filestore.resource";
  public static final String GITOPS_RESOURCE_PACKAGE = "io.harness.ng.gitops.resource";
  public static final String INFRA_RESOURCE_PACKAGE = "io.harness.ng.core.infrastructure.resource";
  public static final String OAUTH_RESOURCE_PACKAGE = "io.harness.ng.oauth";
  public static final String LDAP_PACKAGE = "io.harness.ldap.resource";
  public static final String CHAOS_PACKAGE = "io.harness.ng.chaos";

  public static final String IP_ALLOWLIST_PACKAGE = "io.harness.ipallowlist.resource";
  public static final String SETTINGS_RESOURCE_PACKAGE = "io.harness.ngsettings.remote";
  public static final String FREEZE_RESOURCE_PACKAGE = "io.harness.ng.freeze.resource";
  public static final String MANIFEST_RESOURCE_PACKAGE = "io.harness.ng.core.manifests.resources";
  private static final String REFRESH_RESOURCE_PACKAGE = "io.harness.ng.core.refresh";
  private static final String DEPLOYMENT_STAGE_PACKAGE = "io.harness.ng.core.deploymentstage";
  private static final String SERVICE_ENV_MIGRATION_RESOURCE_PACKAGE =
      "io.harness.ng.core.migration.serviceenvmigrationv2.resources";
  private static final String GCP_PACKAGE = "io.harness.ng.core.gcp.resources";
  private static final String MODULEVERSION_RESOURCE_PACKAGE = "io.harness.ng.moduleversion.resource";
  private static final String TERRAFORM_CLOUD_RESOURCE_PACKAGE = "io.harness.ng.core.terraformcloud.resources";
  private static final String EOL_BANNER_RESOURCE_PACKAGE = "io.harness.ng.core.eolbanner.resources";
  private static final String TERRAFORM_RESOURCE_PACKAGE = "io.harness.ng.core.terraform.resources";

  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @Setter @JsonProperty("mongo") @ConfigSecret private MongoConfig mongoConfig;
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;
  @JsonProperty("disableResourceValidation") private boolean disableResourceValidation;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("allowedOrigins") private List<String> allowedOrigins = Lists.newArrayList();
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("grpcClient") private GrpcClientConfig grpcClientConfig;
  @JsonProperty("grpcServer") private GrpcServerConfig grpcServerConfig;
  @JsonProperty("nextGen") @ConfigSecret private NextGenConfig nextGenConfig;
  @JsonProperty("ciDefaultEntityConfiguration")
  @ConfigSecret
  private CiDefaultEntityConfiguration ciDefaultEntityConfiguration;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerClientConfig;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty("ceNextGenClientConfig") private ServiceHttpClientConfig ceNextGenClientConfig;
  @JsonProperty("cvngClientConfig") private ServiceHttpClientConfig cvngClientConfig;
  @JsonProperty("lightwingClientConfig") private ServiceHttpClientConfig lightwingClientConfig;
  @JsonProperty("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;
  @JsonProperty("chaosServiceClientConfig") private ServiceHttpClientConfig chaosServiceClientConfig;
  @JsonProperty("eventsFramework") @ConfigSecret private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("redisLockConfig") @ConfigSecret private RedisConfig redisLockConfig;
  @JsonProperty(value = "enableAuth", defaultValue = "true") private boolean enableAuth;
  @JsonProperty(value = "ngIteratorsConfig") private NgIteratorsConfig ngIteratorsConfig;
  @JsonProperty("ceAwsSetupConfig") @ConfigSecret @Deprecated private CEAwsSetupConfig ceAwsSetupConfig;
  @JsonProperty("ceAzureSetupConfig") @ConfigSecret private CEAzureSetupConfig ceAzureSetupConfig;
  @JsonProperty("ceGcpSetupConfig") private CEGcpSetupConfig ceGcpSetupConfig;
  @JsonProperty(value = "enableAudit") private boolean enableAudit;
  @JsonProperty(value = "ngAuthUIEnabled") private boolean isNgAuthUIEnabled;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("accessControlClient")
  @ConfigSecret
  private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("accountConfig") private AccountConfig accountConfig;
  @JsonProperty("logStreamingServiceConfig")
  @ConfigSecret
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  private OpaServiceConfiguration opaServerConfig;
  private String policyManagerSecret;
  private ServiceHttpClientConfig opaClientConfig;
  @JsonProperty("gitSyncServerConfig") private GrpcServerConfig gitSyncGrpcServerConfig;
  @JsonProperty("gitGrpcClientConfigs") private Map<Microservice, GrpcClientConfig> gitGrpcClientConfigs;
  @JsonProperty("shouldDeployWithGitSync") private Boolean shouldDeployWithGitSync;
  @JsonProperty("notificationClient")
  @ConfigSecret
  private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("resourceGroupClientConfig") @ConfigSecret private ResourceGroupClientConfig resourceGroupClientConfig;
  @JsonProperty("accessControlAdminClient")
  @ConfigSecret
  private AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;
  @JsonProperty("outboxPollConfig") private OutboxPollConfiguration outboxPollConfig;
  @JsonProperty("segmentConfiguration") @ConfigSecret private SegmentConfiguration segmentConfiguration;
  @JsonProperty("subscriptionConfig") @ConfigSecret private SubscriptionConfig subscriptionConfig;
  @JsonProperty("gitSdkConfiguration") private GitSdkConfiguration gitSdkConfiguration;
  @JsonProperty("fileServiceConfiguration") private FileServiceConfiguration fileServiceConfiguration;
  @JsonProperty("baseUrls") private BaseUrls baseUrls;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;
  @JsonProperty("timescaledb") @ConfigSecret private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("exportMetricsToStackDriver") private boolean exportMetricsToStackDriver;
  @JsonProperty("signupNotificationConfiguration")
  private SignupNotificationConfiguration signupNotificationConfiguration;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty(value = "scopeAccessCheckEnabled", defaultValue = "false") private boolean isScopeAccessCheckEnabled;
  @JsonProperty(value = "signupTargetEnv") private String signupTargetEnv;
  @JsonProperty(value = "delegateStatusEndpoint") private String delegateStatusEndpoint;
  @JsonProperty(value = "gitlabConfig") private GitlabConfig gitlabConfig;
  @JsonProperty(value = "bitbucketConfig") private BitbucketConfig bitbucketConfig;
  @JsonProperty(value = "oauthRefreshFrequency") private long oauthRefreshFrequency;
  @JsonProperty(value = "oauthRefreshEnabled") private boolean oauthRefreshEnabled;
  @JsonProperty(value = "opaConnectivityEnabled") private boolean opaConnectivityEnabled;
  @JsonProperty("hostname") String hostname = "localhost";
  @JsonProperty("basePathPrefix") String basePathPrefix = "";
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("ciManagerClientConfig") ServiceHttpClientConfig ciManagerClientConfig;
  @JsonProperty("secretsConfiguration") private SecretsConfiguration secretsConfiguration;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("ffServerClientConfig") ServiceHttpClientConfig ffServerClientConfig;
  @ConfigSecret @JsonProperty("gitopsResourceClientConfig") GitopsResourceClientConfig gitopsResourceClientConfig;
  @JsonProperty("debeziumConsumersConfigs") DebeziumConsumersConfig debeziumConsumersConfigs;
  @JsonProperty(value = "cdTsDbRetentionPeriodMonths") private String cdTsDbRetentionPeriodMonths;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty("gitService") private GitServiceConfiguration gitServiceConfiguration;
  @JsonProperty(value = "disableFreezeNotificationTemplate") private boolean disableFreezeNotificationTemplate;
  @JsonProperty(value = "pluginExecutionConfig") private PluginExecutionConfig pluginExecutionConfig;
  @JsonProperty("signupDomainDenylistConfig")
  private SignupDomainDenylistConfiguration signupDomainDenylistConfiguration;

  // [secondary-db]: Uncomment this and the corresponding config in yaml file if you want to connect to another database
  //  @JsonProperty("secondary-mongo") MongoConfig secondaryMongoConfig;

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        buildSwaggerBundleConfiguration(HARNESS_RESOURCE_CLASSES);
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(hostname);
    defaultSwaggerBundleConfiguration.setUriPrefix(basePathPrefix);
    defaultSwaggerBundleConfiguration.setTitle("CD NextGen API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");
    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), NextGenConfiguration.CORE_PACKAGE,
                NextGenConfiguration.CONNECTOR_PACKAGE, NextGenConfiguration.GITOPS_PROVIDER_RESOURCE_PACKAGE,
                NextGenConfiguration.GIT_SYNC_PACKAGE, NextGenConfiguration.CDNG_RESOURCES_PACKAGE,
                NextGenConfiguration.OVERLAY_INPUT_SET_RESOURCE_PACKAGE, NextGenConfiguration.YAML_PACKAGE,
                NextGenConfiguration.FILTER_PACKAGE, NextGenConfiguration.SIGNUP_PACKAGE,
                NextGenConfiguration.MOCKSERVER_PACKAGE, NextGenConfiguration.ACCOUNT_PACKAGE,
                NextGenConfiguration.LICENSE_PACKAGE, NextGenConfiguration.SUBSCRIPTION_PACKAGE,
                NextGenConfiguration.CREDIT_PACKAGE, NextGenConfiguration.POLLING_PACKAGE,
                NextGenConfiguration.ENFORCEMENT_PACKAGE, NextGenConfiguration.ENFORCEMENT_CLIENT_PACKAGE,
                NextGenConfiguration.ARTIFACTS_PACKAGE, NextGenConfiguration.AUTHENTICATION_SETTINGS_PACKAGE,
                NextGenConfiguration.CD_OVERVIEW_PACKAGE, NextGenConfiguration.ROLLBACK_PACKAGE,
                NextGenConfiguration.ACTIVITY_HISTORY_PACKAGE, NextGenConfiguration.SERVICE_PACKAGE,
                NextGenConfiguration.SERVICE_ACCOUNTS_PACKAGE, NextGenConfiguration.BUCKETS_PACKAGE,
                NextGenConfiguration.CLUSTER_GCP_PACKAGE, NextGenConfiguration.WEBHOOK_PACKAGE,
                NextGenConfiguration.ENVIRONMENT_PACKAGE, NextGenConfiguration.USERPROFILE_PACKAGE,
                NextGenConfiguration.JIRA_PACKAGE, NextGenConfiguration.EXECUTION_PACKAGE,
                NextGenConfiguration.ENTITYSETUP_PACKAGE, NextGenConfiguration.SCHEMA_PACKAGE,
                NextGenConfiguration.DELEGATE_PACKAGE, NextGenConfiguration.ACCESS_CONTROL_PACKAGE,
                NextGenConfiguration.FEEDBACK_PACKAGE, NextGenConfiguration.INSTANCE_SYNC_PACKAGE,
                NextGenConfiguration.INVITE_PACKAGE, NextGenConfiguration.USER_PACKAGE,
                NextGenConfiguration.INSTANCE_NG_PACKAGE, NextGenConfiguration.LICENSING_USAGE_PACKAGE,
                NextGenConfiguration.SMTP_NG_RESOURCE, NextGenConfiguration.SERVICENOW_PACKAGE,
                NextGenConfiguration.SCIM_NG_RESOURCE, NextGenConfiguration.NG_GLOBAL_KMS_RESOURCE_PACKAGE,
                NextGenConfiguration.ACCOUNT_SETTING_PACKAGE, NextGenConfiguration.ENV_GROUP_RESOURCE,
                NextGenConfiguration.AZURE_RESOURCES_PACKAGE, NextGenConfiguration.NG_TRIAL_SIGNUP_PACKAGE,
                NextGenConfiguration.VARIABLE_RESOURCE_PACKAGE, NextGenConfiguration.FILE_STORE_RESOURCE_PACKAGE,
                NextGenConfiguration.GITOPS_RESOURCE_PACKAGE, NextGenConfiguration.INFRA_RESOURCE_PACKAGE,
                NextGenConfiguration.AWS_PACKAGE, NextGenConfiguration.OAUTH_RESOURCE_PACKAGE,
                NextGenConfiguration.LDAP_PACKAGE, NextGenConfiguration.CHAOS_PACKAGE,
                NextGenConfiguration.SETTINGS_RESOURCE_PACKAGE, NextGenConfiguration.AGENT_PACKAGE,
                NextGenConfiguration.CUSTOM_DEPLOYMENT_PACKAGE, NextGenConfiguration.FREEZE_RESOURCE_PACKAGE,
                NextGenConfiguration.MODULEVERSION_RESOURCE_PACKAGE, NextGenConfiguration.REFRESH_RESOURCE_PACKAGE,
                DEPLOYMENT_STAGE_PACKAGE, NextGenConfiguration.MANIFEST_RESOURCE_PACKAGE,
                NextGenConfiguration.TAS_PACKAGE, NextGenConfiguration.SERVICE_ENV_MIGRATION_RESOURCE_PACKAGE,
                NextGenConfiguration.TERRAFORM_CLOUD_RESOURCE_PACKAGE, NextGenConfiguration.GCP_PACKAGE,
                NextGenConfiguration.EOL_BANNER_RESOURCE_PACKAGE, NextGenConfiguration.TERRAFORM_RESOURCE_PACKAGE,
                NextGenConfiguration.IP_ALLOWLIST_PACKAGE, NextGenConfiguration.SERVICE_OVERRIDES_PACKAGE))
        .collect(Collectors.toSet());
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  public static Set<String> getUniquePackagesContainingResources() {
    return HARNESS_RESOURCE_CLASSES.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("Harness NextGen Software Delivery Platform API Reference")
            .description(
                "This is the Open Api Spec 3 for the NextGen Manager. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    try {
      URL baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    final Set<String> resourceClasses =
        getOAS3ResourceClassesOnly().stream().map(Class::getCanonicalName).collect(toSet());
    return new SwaggerConfiguration()
        .openAPI(oas)
        .prettyPrint(true)
        .resourceClasses(resourceClasses)
        .scannerClass("io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return HARNESS_RESOURCE_CLASSES.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}
