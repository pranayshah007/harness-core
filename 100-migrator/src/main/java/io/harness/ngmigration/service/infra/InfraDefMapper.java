/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;

import software.wings.infra.InfrastructureDefinition;

import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public interface InfraDefMapper {
  ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition);

  InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition);

  Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfigurations);

  default List<String> getConnectorIds(InfrastructureDefinition infrastructureDefinition) {
    return Collections.emptyList();
  }
}
