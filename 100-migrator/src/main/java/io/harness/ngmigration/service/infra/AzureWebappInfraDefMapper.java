/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static io.harness.ngmigration.service.infra.InfraDefMapperUtils.getExpression;

import static software.wings.api.CloudProviderType.AZURE;
import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.AzureWebAppInfra;
import software.wings.infra.AzureWebAppInfra.AzureWebAppInfraKeys;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class AzureWebappInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.AZURE_WEBAPP;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    return InfrastructureType.AZURE_WEB_APP;
  }

  @Override
  public Infrastructure getSpec(MigrationContext migrationContext, InfrastructureDefinition infrastructureDefinition,
      List<ElastigroupConfiguration> elastigroupConfigurations) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    NgEntityDetail connectorDetail;

    if (infrastructureDefinition.getCloudProviderType() == AZURE) {
      AzureWebAppInfra azureWebAppInfra = (AzureWebAppInfra) infrastructureDefinition.getInfrastructure();
      connectorDetail =
          migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(azureWebAppInfra.getCloudProviderId()).build())
              .getNgEntityDetail();

      return AzureWebAppInfrastructure.builder()
          .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
          .subscriptionId(getExpression(azureWebAppInfra.getExpressions(), AzureWebAppInfraKeys.subscriptionId,
              azureWebAppInfra.getSubscriptionId(), infrastructureDefinition.getProvisionerId()))
          .resourceGroup(getExpression(azureWebAppInfra.getExpressions(), AzureWebAppInfraKeys.resourceGroup,
              azureWebAppInfra.getResourceGroup(), infrastructureDefinition.getProvisionerId()))
          .build();
    }

    throw new InvalidRequestException("Unsupported Infra for Azure deployment");
  }
}
