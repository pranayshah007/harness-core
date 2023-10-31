/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.configuration.DeployMode;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;
import io.harness.version.RuntimeInfo;
import io.harness.version.VersionInfoManager;
import io.harness.version.VersionPackage;

import software.wings.app.MainConfiguration;
import software.wings.core.managerConfiguration.ConfigurationController;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Api("version")
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
public class VersionInfoResource {
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private ConfigurationController configurationController;
  @Inject private MainConfiguration mainConfiguration;

  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<VersionPackage> get() {
    final String deployMode = mainConfiguration.getDeployMode().name();
    DeployMode deployMode1 = DeployMode.valueOf(deployMode);
    return new RestResponse<>(VersionPackage.builder()
                                  .versionInfo(versionInfoManager.getVersionInfo())
                                  .runtimeInfo(RuntimeInfo.builder()
                                                   .primary(configurationController.isPrimary())
                                                   .primaryVersion(configurationController.getPrimaryVersion())
                                                   .deployMode(deployMode1.getDeployedAs())
                                                   .build())
                                  .build());
  }
}
