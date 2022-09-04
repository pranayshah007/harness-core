/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.analyserservice;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.mongo.MongoConfig;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;
import io.dropwizard.server.DefaultServerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(PIPELINE)
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalyserServiceConfiguration extends Configuration {
  @JsonProperty("mongo") private MongoConfig mongoConfig;
  @JsonProperty("eventsFramework") private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @JsonProperty("executionTimeLimitMillis") private Long executionTimeLimitMillis;
  @JsonProperty("manyEntriesAlertFactor") private Integer manyEntriesAlertFactor;
  @JsonProperty("aggregateScheduleInterval") private Long aggregateScheduleInterval;
  @Builder.Default @JsonProperty("allowedOrigins") private List<String> allowedOrigins = new ArrayList<>();

  public AnalyserServiceConfiguration() {
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

  private ConnectorFactory getDefaultAdminConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(9096);
    return factory;
  }

  private ConnectorFactory getDefaultApplicationConnectorFactory() {
    final HttpConnectorFactory factory = new HttpConnectorFactory();
    factory.setPort(9015);
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
    return Arrays.asList(mongoConfig.getAliasDBName());
  }
}
