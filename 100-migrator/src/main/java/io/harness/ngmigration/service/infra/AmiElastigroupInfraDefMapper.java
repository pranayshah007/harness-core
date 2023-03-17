/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.service.infra.InfraDefMapperUtils.getExpression;

import static software.wings.api.CloudProviderType.AWS;
import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.AwsAmiInfrastructure.AwsAmiInfrastructureKeys;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class AmiElastigroupInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    if (infrastructureDefinition.getCloudProviderType() == AWS) {
      AwsAmiInfrastructure awsAmiInfrastructure = (AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure();
      if (isNotEmpty(awsAmiInfrastructure.getSpotinstCloudProvider())) {
        return ServiceDefinitionType.ELASTIGROUP;
      } else {
        return ServiceDefinitionType.ASG;
      }
    }
    return ServiceDefinitionType.ELASTIGROUP;
  }

  @Override
  public List<String> getConnectorIds(InfrastructureDefinition infrastructureDefinition) {
    List<String> connectorIds = new ArrayList<>();
    if (infrastructureDefinition.getCloudProviderType() == AWS) {
      AwsAmiInfrastructure awsInfra = (AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure();
      if (isNotEmpty(awsInfra.getSpotinstCloudProvider())) {
        connectorIds.add(awsInfra.getSpotinstCloudProvider());
      }
    }
    return connectorIds;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    if (infrastructureDefinition.getCloudProviderType() == AWS) {
      AwsAmiInfrastructure awsAmiInfrastructure = (AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure();
      if (isNotEmpty(awsAmiInfrastructure.getSpotinstCloudProvider())) {
        return InfrastructureType.ELASTIGROUP;
      } else {
        return InfrastructureType.ASG;
      }
    }
    throw new InvalidRequestException("Unsupported Infra for Ecs deployment");
  }

  @Override
  public Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfigurations) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    NgEntityDetail connectorDetail;
    if (infrastructureDefinition.getCloudProviderType() == AWS) {
      AwsAmiInfrastructure awsAmiInfrastructure = (AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure();
      if (isNotEmpty(awsAmiInfrastructure.getSpotinstCloudProvider())) { // Spot Infra
        connectorDetail =
            migratedEntities
                .get(CgEntityId.builder().type(CONNECTOR).id(awsAmiInfrastructure.getSpotinstCloudProvider()).build())
                .getNgEntityDetail();
        return ElastigroupInfrastructure.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
            .configuration(elastigroupConfigurations.get(0))
            .build();
      } else {
        connectorDetail =
            migratedEntities
                .get(CgEntityId.builder().type(CONNECTOR).id(awsAmiInfrastructure.getCloudProviderId()).build())
                .getNgEntityDetail();
        return AsgInfrastructure.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
            .region(getExpression(awsAmiInfrastructure.getExpressions(), AwsAmiInfrastructureKeys.region,
                awsAmiInfrastructure.getRegion(), infrastructureDefinition.getProvisionerId()))
            .build();
      }
    }
    throw new InvalidRequestException("Unsupported Infra for AMI deployment");
  }
}
