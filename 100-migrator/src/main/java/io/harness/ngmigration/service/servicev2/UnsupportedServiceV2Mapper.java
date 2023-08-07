/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.ngmigration.beans.MigrationContext;

import software.wings.beans.Service;
import software.wings.service.intfc.WorkflowService;

import java.util.List;

public class UnsupportedServiceV2Mapper implements ServiceV2Mapper {
  @Override
  public boolean isMigrationSupported() {
    return false;
  }

  @Override
  public ServiceDefinition getServiceDefinition(WorkflowService workflowService, MigrationContext migrationContext,
      Service service, List<ManifestConfigWrapper> manifests, List<ConfigFileWrapper> configFiles,
      List<StartupScriptConfiguration> startupScriptConfigurations) {
    return null;
  }
}
