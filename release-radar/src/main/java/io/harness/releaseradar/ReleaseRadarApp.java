/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.releaseradar;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import io.harness.maintenance.MaintenanceController;
import io.harness.reflection.HarnessReflections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.model.Resource;

import javax.ws.rs.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ReleaseRadarApp extends Application<AppConfig> {
  public static void main(String[] args) throws Exception {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.warn("Shutdown hook, entering maintenance...");
      MaintenanceController.forceMaintenance(true);
    }));
    new ReleaseRadarApp().run(args);
  }

  @Override
  public void run(AppConfig configuration, Environment environment) throws Exception {
    log.info("Entering startup maintenance mode");
    MaintenanceController.forceMaintenance(true);
    log.info("Starting ReleaseRadar Application ...");

    List<Module> modules = new ArrayList<>();
    modules.add(ReleaseRadarAppModule.getInstance(configuration));
    Injector injector = Guice.createInjector(modules);


    // Will create collections and Indexes
    //    injector.getInstance(HPersistence.class);
    registerResources(configuration, environment, injector);
  }

  private void registerResources(AppConfig appConfig, Environment environment, Injector injector) {
    List<Class> resourceClasses =
        HarnessReflections.get()
            .getTypesAnnotatedWith(Path.class)
            .stream()
            .filter(
                klazz -> StringUtils.startsWithAny(klazz.getPackage().getName(), "io.harness.releaseradar.resources"))
            .collect(Collectors.toList());
    for (Class<?> resource : resourceClasses) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().property(ServerProperties.RESOURCE_VALIDATION_DISABLE, true);
  }
}
