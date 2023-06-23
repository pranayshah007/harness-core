/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.swagger.SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration;

import static com.google.common.collect.ImmutableMap.of;
import static java.util.stream.Collectors.toSet;

import io.harness.AccessControlClientConfiguration;
import io.harness.ScmConnectionConfig;
import io.harness.beans.entities.IACMServiceConfig;
import io.harness.cache.CacheConfig;
import io.harness.cf.CfClientConfig;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.enforcement.client.EnforcementClientConfiguration;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.ff.FeatureFlagConfig;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.lock.DistributedLockImplementation;
import io.harness.mongo.MongoConfig;
import io.harness.redis.RedisConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secret.ConfigSecret;
import io.harness.ssca.beans.entities.SSCAServiceConfig;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.telemetry.segment.SegmentConfiguration;
import io.harness.threading.ThreadPoolConfig;
import io.harness.timescaledb.TimeScaleDBConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.bundles.assets.AssetsBundleConfiguration;
import io.dropwizard.bundles.assets.AssetsConfiguration;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class CIManagerConfiguration extends Configuration implements AssetsBundleConfiguration {
  public static final String BASE_PACKAGE = "io.harness.app.resources";

  public static final String CI_API_PACKAGE = "io.harness.ci.api";
  public static final String NG_PIPELINE_PACKAGE = "io.harness.ngpipeline";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";
  public static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  @JsonProperty
  private AssetsConfiguration assetsConfiguration =
      AssetsConfiguration.builder()
          .mimeTypes(of("js", "application/json; charset=UTF-8", "zip", "application/zip"))
          .build();
  @Builder.Default @JsonProperty("cimanager-mongo") private MongoConfig harnessCIMongo = MongoConfig.builder().build();
  @Builder.Default @JsonProperty("harness-mongo") private MongoConfig harnessMongo = MongoConfig.builder().build();
  @JsonProperty("pmsMongo") private MongoConfig pmsMongoConfig;
  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  private ScmConnectionConfig scmConnectionConfig;

  @JsonProperty("managerClientConfig") private ServiceHttpClientConfig managerClientConfig;
  @JsonProperty("ngManagerClientConfig") private ServiceHttpClientConfig ngManagerClientConfig;
  @JsonProperty("timescaledb") private TimeScaleDBConfig timeScaleDBConfig;
  @JsonProperty("accessControlClient") private AccessControlClientConfiguration accessControlClientConfiguration;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("cacheConfig") private CacheConfig cacheConfig;
  @JsonProperty("enforcementClientConfiguration") EnforcementClientConfiguration enforcementClientConfiguration;
  @JsonProperty("pmsSdkOrchestrationEventPoolConfig") private ThreadPoolConfig pmsSdkOrchestrationEventPoolConfig;
  @JsonProperty("pmsPlanCreatorServicePoolConfig") private ThreadPoolConfig pmsPlanCreatorServicePoolConfig;
  @JsonProperty("asyncDelegateResponseConsumption") private ThreadPoolConfig asyncDelegateResponseConsumption;
  @JsonProperty("segmentConfiguration") @ConfigSecret private SegmentConfiguration segmentConfiguration;
  @JsonProperty("redisLockConfig") private RedisConfig redisLockConfig;
  @JsonProperty("distributedLockImplementation") private DistributedLockImplementation distributedLockImplementation;
  @JsonProperty("pmsSdkExecutionPoolConfig") private ThreadPoolConfig pmsSdkExecutionPoolConfig;
  @JsonProperty("cfClientConfig") @ConfigSecret private CfClientConfig cfClientConfig;
  @JsonProperty("featureFlagConfig") private FeatureFlagConfig featureFlagConfig;

  private String ngManagerServiceSecret;
  private LogServiceConfig logServiceConfig;
  private TIServiceConfig tiServiceConfig;
  private STOServiceConfig stoServiceConfig;
  private SSCAServiceConfig sscaServiceConfig;
  private IACMServiceConfig iacmServiceConfig;

  private String managerServiceSecret;
  private String jwtAuthSecret;
  private String jwtIdentityServiceSecret;
  private boolean enableAuth;
  private String managerTarget;
  private String managerAuthority;
  private CIExecutionServiceConfig ciExecutionServiceConfig;
  private ServiceHttpClientConfig opaClientConfig;
  private String policyManagerSecret;

  @JsonProperty("pmsSdkGrpcServerConfig") private GrpcServerConfig pmsSdkGrpcServerConfig;
  @JsonProperty("pmsGrpcClientConfig") private GrpcClientConfig pmsGrpcClientConfig;
  @JsonProperty("shouldConfigureWithPMS") private Boolean shouldConfigureWithPMS;
  @JsonProperty("enableDashboardTimescale") private Boolean enableDashboardTimescale;
  @JsonProperty("apiUrl") private String apiUrl;
  @JsonProperty("hostname") String hostname;
  @JsonProperty("basePathPrefix") String basePathPrefix;
  @JsonProperty(value = "enableOpentelemetry") private Boolean enableOpentelemetry;
  @JsonProperty("enableTelemetry") private Boolean enableTelemetry;

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), BASE_PACKAGE, CI_API_PACKAGE,
                NG_PIPELINE_PACKAGE, ENFORCEMENT_CLIENT_PACKAGE))
        .collect(Collectors.toSet());
  }

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        buildSwaggerBundleConfiguration(HARNESS_RESOURCE_CLASSES);
    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(
        "localhost"); // TODO, we should set the appropriate host here ex: qa.harness.io etc
    defaultSwaggerBundleConfiguration.setTitle("CI API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");

    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  @Override
  public AssetsConfiguration getAssetsConfiguration() {
    return assetsConfiguration;
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return HARNESS_RESOURCE_CLASSES.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  @JsonIgnore
  public OpenAPIConfiguration getOasConfig() {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("CIE API Reference")
            .description(
                "This is the Open Api Spec 3 for the CIE Manager. This is under active development. Beware of the breaking change with respect to the generated code stub")
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
    Set<String> packages = getUniquePackages(getOAS3ResourceClassesOnly());
    return new SwaggerConfiguration().openAPI(oas).prettyPrint(true).resourcePackages(packages).scannerClass(
        "io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (harnessCIMongo != null) {
      dbAliases.add(harnessCIMongo.getAliasDBName());
    }
    if (harnessMongo != null) {
      dbAliases.add(harnessMongo.getAliasDBName());
    }
    if (pmsMongoConfig != null) {
      dbAliases.add(pmsMongoConfig.getAliasDBName());
    }
    return dbAliases;
  }
}
