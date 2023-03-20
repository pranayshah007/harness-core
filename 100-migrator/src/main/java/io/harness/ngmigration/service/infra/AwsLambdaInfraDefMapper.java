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
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.infra.AwsLambdaInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class AwsLambdaInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.AWS_LAMBDA;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    if (infrastructureDefinition.getCloudProviderType() == CloudProviderType.AWS
        && infrastructureDefinition.getDeploymentType() == DeploymentType.AWS_LAMBDA) {
      return InfrastructureType.AWS_LAMBDA;
    }
    throw new InvalidRequestException("Unsupported Infra for AWS Lambda deployment");
  }

  @Override
  public Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfigurations) {
    NgEntityDetail connectorDetail;
    if (infrastructureDefinition.getCloudProviderType() == CloudProviderType.AWS
        && infrastructureDefinition.getDeploymentType() == DeploymentType.AWS_LAMBDA) {
      AwsLambdaInfrastructure infrastructure = (AwsLambdaInfrastructure) infrastructureDefinition.getInfrastructure();
      connectorDetail = migrationContext.getMigratedEntities()
                            .get(CgEntityId.builder().type(CONNECTOR).id(infrastructure.getCloudProviderId()).build())
                            .getNgEntityDetail();

      return io.harness.cdng.infra.yaml.AwsLambdaInfrastructure.builder()
          .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
          .region(ParameterField.createValueField(infrastructure.getRegion()))
          .build();
    }
    throw new InvalidRequestException("Unsupported Infra for AWS Lambda deployment");
  }
}
