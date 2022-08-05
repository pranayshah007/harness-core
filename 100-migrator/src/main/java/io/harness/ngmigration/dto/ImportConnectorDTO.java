/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.ImportMechanism;
import io.harness.ngmigration.beans.MigrationInputDTO;

import software.wings.settings.SettingVariableTypes;

import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDC)
@Data
@EqualsAndHashCode(callSuper = true)
public class ImportConnectorDTO extends MigrationInputDTO {
  // Do we want to import everything? Only a specific type of connectors e.g. just Docker, AWS? Or a specific set of
  // connectors
  @NotNull private ImportMechanism mechanism;
  private String orgId;
  private String projectId;
  private List<String> ids;
  private Set<SettingVariableTypes> types;
}
