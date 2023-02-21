/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.RUNTIME_INPUT;

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
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.AzureWebAppInfra;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

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
  public Infrastructure getSpec(MigrationInputDTO inputDTO, InfrastructureDefinition infrastructureDefinition,
      Map<CgEntityId, NGYamlFile> migratedEntities, Map<CgEntityId, CgEntityNode> entities,
      List<ElastigroupConfiguration> elastigroupConfigurations) {
    NgEntityDetail connectorDetail;

    if (infrastructureDefinition.getCloudProviderType() == AZURE) {
      AzureWebAppInfra azureWebAppInfra = (AzureWebAppInfra) infrastructureDefinition.getInfrastructure();
      connectorDetail =
          migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(azureWebAppInfra.getCloudProviderId()).build())
              .getNgEntityDetail();
      String subscriptionId = azureWebAppInfra.getSubscriptionId();
      if (isEmpty(subscriptionId) && isNotEmpty(azureWebAppInfra.getExpressions())
          && azureWebAppInfra.getExpressions().containsKey("subscriptionId")) {
        subscriptionId = azureWebAppInfra.getExpressions().get("subscriptionId");
      }

      String resourceGroup = azureWebAppInfra.getResourceGroup();
      if (isEmpty(resourceGroup) && isNotEmpty(azureWebAppInfra.getExpressions())
          && azureWebAppInfra.getExpressions().containsKey("resourceGroup")) {
        resourceGroup = azureWebAppInfra.getExpressions().get("resourceGroup");
      }

      return AzureWebAppInfrastructure.builder()
          .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
          .subscriptionId(isEmpty(subscriptionId) ? RUNTIME_INPUT : ParameterField.createValueField(subscriptionId))
          .resourceGroup(isEmpty(resourceGroup) ? RUNTIME_INPUT : ParameterField.createValueField(resourceGroup))
          .build();
    }

    throw new InvalidRequestException("Unsupported Infra for Azure deployment");
  }
}
