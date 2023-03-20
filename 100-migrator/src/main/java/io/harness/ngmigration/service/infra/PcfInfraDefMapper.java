/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.api.CloudProviderType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.PcfInfraStructure;
import software.wings.ngmigration.CgEntityId;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class PcfInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.TAS;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    if (infrastructureDefinition.getCloudProviderType() == CloudProviderType.PCF) {
      return InfrastructureType.TAS;
    }
    throw new InvalidRequestException("Unsupported Infra for Pcf deployment");
  }

  @Override
  public Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfiguration) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    NgEntityDetail connectorDetail;
    if (infrastructureDefinition.getCloudProviderType() == CloudProviderType.PCF) {
      PcfInfraStructure pcfInfraStructure = (PcfInfraStructure) infrastructureDefinition.getInfrastructure();
      connectorDetail =
          migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(pcfInfraStructure.getCloudProviderId()).build())
              .getNgEntityDetail();
      return TanzuApplicationServiceInfrastructure.builder()
          .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
          .space(ParameterField.createValueField(pcfInfraStructure.getSpace()))
          .organization(ParameterField.createValueField(pcfInfraStructure.getOrganization()))
          .build();
    }
    throw new InvalidRequestException("Unsupported Infra for Pcf deployment");
  }
}
