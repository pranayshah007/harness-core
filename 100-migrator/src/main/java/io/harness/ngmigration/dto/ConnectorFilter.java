/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ngmigration.beans.ImportMechanism;

import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("CONNECTOR")
public class ConnectorFilter extends Filter {
  @Parameter(
      description =
          "ALL: To migrate all connectors. TYPE: To migrate only specific type of connectors(eg: Docker, AWS etc). ID: TO migrate only specific connectors")
  @NotNull
  private ImportMechanism importType;

  @Parameter(description = "To be provided if mechanism is ID") private List<String> ids;

  @Parameter(description = "To be provided if mechanism is TYPE") private Set<SettingVariableTypes> types;
}
