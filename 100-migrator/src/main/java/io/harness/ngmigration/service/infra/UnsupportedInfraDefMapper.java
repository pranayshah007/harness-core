/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.NGYamlFile;

import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class UnsupportedInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return null;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    return null;
  }

  @Override
  public Infrastructure getSpec(
      InfrastructureDefinition infrastructureDefinition, Map<CgEntityId, NGYamlFile> migratedEntities) {
    return null;
  }
}
