/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.app;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.resources.DelegateSetupResource;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.reflection.HarnessReflections;
import io.harness.swagger.SwaggerBundleConfigurationFactory;
import io.harness.threading.ThreadPoolConfig;

import software.wings.beans.HttpMethod;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.Level;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

/**
 * Used to load all the delegate mgr configuration.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Singleton
@OwnedBy(DEL)
public class DelegateServiceConfiguration extends Configuration {
  @JsonProperty("commonPoolConfig") private ThreadPoolConfig commonPoolConfig;

  public DelegateServiceConfiguration() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    defaultServerFactory.setJerseyRootPath("/api");
    defaultServerFactory.setRegisterDefaultExceptionMappers(false);
    defaultServerFactory.setRequestLogFactory(getDefaultlogbackAccessRequestLogFactory());
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
   * Gets swagger bundle configuration.
   *
   * @return the swagger bundle configuration
   */
  public SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    Collection<Class<?>> resourceClasses = getResourceClasses();
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration(resourceClasses);
    defaultSwaggerBundleConfiguration.setResourcePackage("io.harness.delegate.resources");
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost("{{host}}");
    defaultSwaggerBundleConfiguration.setTitle("Delegate Service API Reference");
    return defaultSwaggerBundleConfiguration;
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        // below package will be changed to io.harness.dms.resources later when we have resources in DMS
        .filter(klazz -> StringUtils.startsWithAny(klazz.getPackage().getName(), "io.harness.delegate.resources"))
        .collect(Collectors.toList());
  }
}
