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
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.api.CloudProviderType;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class EcsInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.ECS;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    if (infrastructureDefinition.getCloudProviderType() == CloudProviderType.AWS) {
      return InfrastructureType.ECS;
    }
    throw new InvalidRequestException("Unsupported Infra for Ecs deployment");
  }

  @Override
  public Infrastructure getSpec(MigrationInputDTO inputDTO, InfrastructureDefinition infrastructureDefinition,
      Map<CgEntityId, NGYamlFile> migratedEntities, List<ElastigroupConfiguration> elastigroupConfiguration) {
    NgEntityDetail connectorDetail;
    if (infrastructureDefinition.getCloudProviderType() == CloudProviderType.AWS) {
      AwsEcsInfrastructure ecsInfrastructure = (AwsEcsInfrastructure) infrastructureDefinition.getInfrastructure();
      connectorDetail =
          migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(ecsInfrastructure.getCloudProviderId()).build())
              .getNgEntityDetail();
      return EcsInfrastructure.builder()
          .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
          .region(ParameterField.createValueField(ecsInfrastructure.getRegion()))
          .cluster(ParameterField.createValueField(ecsInfrastructure.getClusterName()))
          .build();
    }
    throw new InvalidRequestException("Unsupported Infra for Ecs deployment");
  }
}
