/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.app;

import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;

import static java.util.Collections.singletonList;

import io.harness.AccessControlClientConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.CacheConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.idp.onboarding.config.OnboardingModuleConfig;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.proxy.config.ProxyAllowListConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.logstreaming.LogStreamingServiceConfiguration;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.notification.NotificationClientConfiguration;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.telemetry.segment.SegmentConfiguration;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.server.ServerFactory;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Getter
@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class IdpConfiguration extends Configuration {
  @Setter @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("logStreamingServiceConfig")
  @ConfigSecret
  private LogStreamingServiceConfiguration logStreamingServiceConfig;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("ngManagerServiceHttpClientConfig") private ServiceHttpClientConfig ngManagerServiceHttpClientConfig;
  @JsonProperty("ngManagerServiceSecret") private String ngManagerServiceSecret;
  @JsonProperty("managerServiceSecret") private String managerServiceSecret;
  @JsonProperty("backstageHttpClientConfig") private ServiceHttpClientConfig backstageHttpClientConfig;
  @JsonProperty("backstageServiceSecret") private String backstageServiceSecret;
  @JsonProperty("idpServiceSecret") private String idpServiceSecret;
  @JsonProperty("jwtAuthSecret") private String jwtAuthSecret;
  @JsonProperty("jwtIdentityServiceSecret") private String jwtIdentityServiceSecret;
  @JsonProperty("onboardingModuleConfig") private OnboardingModuleConfig onboardingModuleConfig;
  @JsonProperty("gitManagerGrpcClientConfig") private GrpcClientConfig gitManagerGrpcClientConfig;
  @JsonProperty("grpcNegotiationType") NegotiationType grpcNegotiationType;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("backstageSaToken") private String backstageSaToken;
  @JsonProperty("backstageSaCaCrt") private String backstageSaCaCrt;
  @JsonProperty("backstageMasterUrl") private String backstageMasterUrl;
  @JsonProperty("backstagePodLabel") private String backstagePodLabel;
  @JsonProperty("env") private String env;
  @JsonProperty("prEnvDefaultBackstageNamespace") private String prEnvDefaultBackstageNamespace;
  @JsonProperty(PROVISION_MODULE_CONFIG) private ProvisionModuleConfig provisionModuleConfig;
  @JsonProperty("backstageAppBaseUrl") private String backstageAppBaseUrl;
  @JsonProperty("backstagePostgresHost") private String backstagePostgresHost;
  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("delegateSelectorsCacheMode") private String delegateSelectorsCacheMode;
  @JsonProperty("idpEncryptionSecret") private String idpEncryptionSecret;
  @JsonProperty("proxyAllowList") private ProxyAllowListConfig proxyAllowList;
  @JsonProperty("shouldConfigureWithNotification") private Boolean shouldConfigureWithNotification;
  @JsonProperty("notificationClient") private NotificationClientConfiguration notificationClientConfiguration;
  @JsonProperty("notificationConfigs") private HashMap<String, String> notificationConfigs;
  @JsonProperty("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceConfiguration;
  @JsonProperty("pipelineServiceSecret") private String pipelineServiceSecret;
  @JsonProperty("jwtExternalServiceSecret") private String jwtExternalServiceSecret;
  @JsonProperty("tiServiceConfig") private TIServiceConfig tiServiceConfig;
  @JsonProperty("scorecardScoreComputationIteratorConfig")
  private IteratorConfig scorecardScoreComputationIteratorConfig;
  @JsonProperty("cpu") private String cpu;
  @JsonProperty("scoreComputerThreadsPerCore") private String scoreComputerThreadsPerCore;
  @JsonProperty("auditClientConfig") private ServiceHttpClientConfig auditClientConfig;
  @JsonProperty("enableAudit") private boolean enableAudit;
  private String managerTarget;
  private String managerAuthority;
  @JsonProperty("streamPerServiceConfiguration") private boolean streamPerServiceConfiguration;
  @JsonProperty("internalAccounts") private List<String> internalAccounts;
  @JsonProperty("segmentConfiguration") private SegmentConfiguration segmentConfiguration;

  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();
  public static final String IDP_SPEC_PACKAGE = "io.harness.spec.server.idp.v1";
  public static final String SERVICES_PROXY_PACKAGE = "io.harness.idp.proxy.services";
  public static final String DELEGATE_PROXY_PACKAGE = "io.harness.idp.proxy.delegate";
  public static final String IDP_HEALTH_PACKAGE = "io.harness.idp.health";

  public IdpConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/");
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

  private ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12003);
    return factory;
  }

  private ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(12004);
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

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (mongoConfig != null) {
      dbAliases.add(mongoConfig.getAliasDBName());
    }
    return dbAliases;
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), IDP_SPEC_PACKAGE, SERVICES_PROXY_PACKAGE,
                DELEGATE_PROXY_PACKAGE, IDP_HEALTH_PACKAGE))
        .collect(Collectors.toSet());
  }
}
