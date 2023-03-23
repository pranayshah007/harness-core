/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.ngmigration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDC)
public enum NGMigrationEntityType {
  WORKFLOW_EXECUTION,
  TEMPLATE,
  SERVICE_COMMAND_TEMPLATE,
  ACCOUNT,
  APPLICATION,
  DUMMY_HEAD,
  WORKFLOW,
  PIPELINE("pipelines"),
  ARTIFACT_STREAM,
  CONNECTOR("connectors"),
  INFRA_PROVISIONER,
  SERVICE,
  ENVIRONMENT,
  SECRET("secrets"),
  INFRA,
  SECRET_MANAGER,
  SERVICE_VARIABLE,
  USER_GROUP,
  CONFIG_FILE,
  ECS_SERVICE_SPEC,
  CONTAINER_TASK,
  TRIGGER,
  MANIFEST,
  FILE_STORE,
  AMI_STARTUP_SCRIPT,
  SECRET_MANAGER_TEMPLATE,
  ELASTIGROUP_CONFIGURATION;

  private String yamlFolderName;

  NGMigrationEntityType(String yamlFolderName) {
    this.yamlFolderName = yamlFolderName;
  }

  NGMigrationEntityType() {}

  public String getYamlFolderName() {
    return yamlFolderName;
  }
}
