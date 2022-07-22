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
  ACCOUNT,
  APPLICATION,
  DUMMY_HEAD,
  WORKFLOW,
  PIPELINE("pipelines"),
  ARTIFACT_STREAM,
  CONNECTOR("connectors"),
  SERVICE,
  ENVIRONMENT,
  SECRET("secrets"),
  INFRA,
  SECRET_MANAGER,
  MANIFEST;

  private String yamlFolderName;

  NGMigrationEntityType(String yamlFolderName) {
    this.yamlFolderName = yamlFolderName;
  }

  NGMigrationEntityType() {}

  public String getYamlFolderName() {
    return yamlFolderName;
  }
}
