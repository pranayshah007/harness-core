/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc;

import static io.harness.rule.OwnerRule.HANTANG;

import io.harness.category.element.E2ETests;
import io.harness.govern.ProviderModule;
import io.harness.grpc.server.GrpcServerConfig;
import io.harness.perpetualtask.PerpetualTaskServiceModule;
import io.harness.rule.Owner;
import io.harness.service.impl.agent.mtls.AgentMtlsEndpointServiceReadOnlyImpl;
import io.harness.service.intfc.AgentMtlsEndpointService;

import software.wings.app.MainConfiguration;
import software.wings.integration.IntegrationTestBase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@Slf4j
@RunWith(JUnit4.class)
public class GrpcServiceConfigurationModuleTest extends IntegrationTestBase {
  final ObjectMapper objectMapper = Jackson.newObjectMapper();
  final Validator validator = Validators.newValidator();
  final YamlConfigurationFactory<MainConfiguration> factory =
      new YamlConfigurationFactory<>(MainConfiguration.class, validator, objectMapper, "dw");

  @Test
  @Owner(developers = HANTANG)
  @Category(E2ETests.class)
  @Ignore("This test should be enabled after the gRPC module removes dependency on PerpetualTaskService.")
  public void test() throws Exception {
    final File yaml = new File(Resources.getResource("config.yml").toURI());
    final MainConfiguration configuration = factory.build(yaml);

    List<Module> modules = new ArrayList<>();
    modules.add(new ProviderModule() {
      @Provides
      public GrpcServerConfig getGrpcServerConfig() {
        return configuration.getGrpcServerConfig();
      }
    });
    modules.add(new PerpetualTaskServiceModule());
    modules.add(new GrpcServiceConfigurationModule(
        configuration.getGrpcServerConfig(), configuration.getPortal().getJwtNextGenManagerSecret()));

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(AgentMtlsEndpointService.class).to(AgentMtlsEndpointServiceReadOnlyImpl.class);
      }
    });
    Injector injector = Guice.createInjector(modules);
    injector.getInstance(ServiceManager.class).startAsync().awaitHealthy();
  }
}
