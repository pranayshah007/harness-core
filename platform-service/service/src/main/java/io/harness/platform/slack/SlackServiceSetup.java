/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.platform.slack;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.platform.PlatformConfiguration.SLACK_RESOURCES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.health.HealthService;
import io.harness.persistence.HPersistence;
import io.harness.platform.remote.VersionInfoResource;
import io.harness.slack.SlackModuleConfig;

import com.google.inject.Injector;
import groovy.util.logging.Slf4j;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.server.model.Resource;

@Slf4j
@OwnedBy(CDC)
public class SlackServiceSetup {
  public static final String SLACK_SERVICE = "SlackService";

  public SlackServiceSetup() {
    // sonar
  }

  public void setup(SlackModuleConfig slackConfig, Environment environment, Injector injector) {
    // Will create collections and Indexes
    registerResources(environment, injector);
    //        registerHealthCheck(environment, injector);
  }

  private void registerHealthCheck(Environment environment, Injector injector) {
    final HealthService healthService = injector.getInstance(HealthService.class);
    environment.healthChecks().register("Slack Application", healthService);
    healthService.registerMonitor(injector.getInstance(HPersistence.class));
  }

  private void registerResources(Environment environment, Injector injector) {
    for (Class<?> resource : SLACK_RESOURCES) {
      if (Resource.isAcceptable(resource)) {
        environment.jersey().register(injector.getInstance(resource));
      }
    }
    environment.jersey().register(injector.getInstance(VersionInfoResource.class));
  }
}
