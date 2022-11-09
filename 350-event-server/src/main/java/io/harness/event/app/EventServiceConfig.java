/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.app;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.config.EventDataBatchQueryConfig;
import io.harness.grpc.server.Connector;
import io.harness.mongo.MongoConfig;
import io.harness.secret.ConfigSecret;
import io.harness.secret.SecretsConfiguration;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.dropwizard.Configuration;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.Path;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Getter
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CE)
public class EventServiceConfig extends Configuration {
  public static final String SERVICE_ROOT_PATH = "/ccmevent";
  public static final String SERVICE_ID = "event-service";
  public static final String BASE_PACKAGE = "io.harness.event";
  public static final String FILTER_PACKAGE = "io.harness.filter";

  public static final List<String> RESOURCE_PACKAGES = ImmutableList.of("io.harness.event.resources");
  public static final String LICENSE_PACKAGE = "io.harness.licensing.usage.resources";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";

  @JsonProperty("swagger") private SwaggerBundleConfiguration swaggerBundleConfiguration;
  @Builder.Default
  @JsonProperty("harness-mongo")
  @ConfigSecret
  private MongoConfig harnessMongo = MongoConfig.builder().build();
  @Builder.Default
  @JsonProperty("events-mongo")
  @ConfigSecret
  private MongoConfig eventsMongo = MongoConfig.builder().build();
  @JsonProperty("secretsConfiguration") private SecretsConfiguration secretsConfiguration;
  @JsonProperty("eventDataBatchQueryConfig") private EventDataBatchQueryConfig eventDataBatchQueryConfig;

  @JsonProperty(value = "hostname") private String hostname = "localhost";
  @JsonProperty(value = "basePathPrefix") private String basePathPrefix = "";

  @Singular private List<Connector> connectors;

  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration defaultSwaggerConf = new SwaggerBundleConfiguration();

    String resourcePackage = String.join(",", getUniquePackages(getResourceClasses()));
    defaultSwaggerConf.setResourcePackage(resourcePackage);
    defaultSwaggerConf.setSchemes(new String[] {"https", "http"});
    defaultSwaggerConf.setTitle("CE Event Service API Reference");
    defaultSwaggerConf.setVersion("1.0");

    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerConf);
  }

  private static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }

  public static Collection<Class<?>> getResourceClasses() {
    final Reflections reflections = new Reflections(RESOURCE_PACKAGES);

    return reflections.getTypesAnnotatedWith(Path.class);
  }

  public EventServiceConfig() {
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

  public List<String> getDbAliases() {
    List<String> dbAliases = new ArrayList<>();
    if (harnessMongo != null) {
      dbAliases.add(harnessMongo.getAliasDBName());
    }
    if (eventsMongo != null) {
      dbAliases.add(eventsMongo.getAliasDBName());
    }
    return dbAliases;
  }
}
