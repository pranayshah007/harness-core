/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.pms.sdk.core.PmsSdkCoreConfig;
import io.harness.pms.sdk.core.PmsSdkCoreModule;
import io.harness.pms.sdk.execution.PmsSdkEventsFrameworkModule;
import io.harness.pms.sdk.registries.PmsSdkRegistryModule;
import io.harness.testing.TestExecution;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@SuppressWarnings("ALL")
public class PmsSdkModule extends AbstractModule {
  private static PmsSdkModule instance;
  private final PmsSdkConfiguration config;

  public static PmsSdkModule getInstance(PmsSdkConfiguration config) {
    if (instance == null) {
      instance = new PmsSdkModule(config);
    }
    return instance;
  }

  private PmsSdkModule(PmsSdkConfiguration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    List<Module> modules = getModules();
    for (Module module : modules) {
      install(module);
    }
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
    requireBinding(ExceptionManager.class);

    MapBinder<String, TestExecution> testExecutionMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
    //    testExecutionMapBinder.addBinding("RecasterAlias Registration")
    //        .toInstance(PmsSdkComponentTester::testRecasterAlias);
    testExecutionMapBinder.addBinding("RecasterAlias Immutablity")
        .toInstance(PmsSdkComponentTester::ensureRecasterAliasImmutability);
  }

  @NotNull
  private List<Module> getModules() {
    List<Module> modules = new ArrayList<>();
    modules.add(
        PmsSdkCoreModule.getInstance(PmsSdkCoreConfig.builder()
                                         .serviceName(config.getServiceName())
                                         .grpcClientConfig(config.getPmsGrpcClientConfig())
                                         .grpcServerConfig(config.getGrpcServerConfig())
                                         .sdkDeployMode(config.getDeploymentMode())
                                         .eventsFrameworkConfiguration(config.getEventsFrameworkConfiguration())
                                         .executionPoolConfig(config.getExecutionPoolConfig())
                                         .orchestrationEventPoolConfig(config.getOrchestrationEventPoolConfig())
                                         .planCreatorServicePoolConfig(config.getPlanCreatorServiceInternalConfig())
                                         .pipelineSdkRedisEventsConfig(config.getPipelineSdkRedisEventsConfig())
                                         .build()));
    modules.add(
        PmsSdkEventsFrameworkModule.getInstance(config.getEventsFrameworkConfiguration(), config.getServiceName()));
    modules.add(PmsSdkRegistryModule.getInstance(config));
    modules.add(PmsSdkProviderModule.getInstance(config));
    modules.add(SdkMonitoringModule.getInstance());
    return modules;
  }
}
