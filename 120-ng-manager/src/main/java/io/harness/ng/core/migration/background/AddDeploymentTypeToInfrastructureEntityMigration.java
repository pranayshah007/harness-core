/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.migration.NGMigration;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class AddDeploymentTypeToInfrastructureEntityMigration implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  private static final String DEBUG_LOG = "[InfrastructureDeploymentTypeMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of deployment type to infrastructureEntity");
      Query<InfrastructureEntity> infrastructureEntityQuery = persistence.createQuery(InfrastructureEntity.class);
      try (HIterator<InfrastructureEntity> iterator = new HIterator<>(infrastructureEntityQuery.fetch())) {
        for (InfrastructureEntity infrastructureEntity : iterator) {
          if (infrastructureEntity.getDeploymentType() != null) {
            log.info(String.format(
                "%s Skipping since infra with identifier %s in account %s, org %s, project %s already has deployment type as %s",
                DEBUG_LOG, infrastructureEntity.getIdentifier(), infrastructureEntity.getAccountId(),
                infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier(),
                infrastructureEntity.getDeploymentType()));
            continue;
          }
          if (infrastructureEntity.getType() == null) {
            log.warn(String.format(
                "%s Skipping because infrastructure type does not exist for infra with identifier %s in account %s, org %s, project %s",
                DEBUG_LOG, infrastructureEntity.getIdentifier(), infrastructureEntity.getAccountId(),
                infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier()));
            continue;
          }
          ServiceDefinitionType serviceDefinitionType =
              mapInfraTypeToServiceDefinitionType(infrastructureEntity.getType());
          if (serviceDefinitionType == null) {
            log.warn(String.format(
                "%s Skipping because infrastructure type %s could not be mapped to serviceDefinitionType for infra with identifier %s in account %s, org %s, project %s",
                DEBUG_LOG, infrastructureEntity.getType(), infrastructureEntity.getIdentifier(),
                infrastructureEntity.getAccountId(), infrastructureEntity.getOrgIdentifier(),
                infrastructureEntity.getProjectIdentifier()));
            continue;
          }
          String newYaml = addDeploymentTypeToYaml(infrastructureEntity, serviceDefinitionType);
          if (EmptyPredicate.isEmpty(newYaml)) {
            continue;
          }

          InfrastructureEntity modifiedInfra =
              infrastructureEntity.withYaml(newYaml).withDeploymentType(serviceDefinitionType);

          boolean valid = validateYaml(modifiedInfra);
          if (!valid) {
            continue;
          }

          infrastructureEntityService.upsert(modifiedInfra, UpsertOptions.DEFAULT.withNoOutbox());
        }
      }
      log.info(DEBUG_LOG + "Migration of adding deployment type to infrastructureEntity completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration of deployment type to infrastructure failed.", e);
    }
  }

  private boolean validateYaml(InfrastructureEntity infrastructureEntity) {
    try {
      YamlPipelineUtils.read(infrastructureEntity.getYaml(), InfrastructureConfig.class);
    } catch (IOException e) {
      log.error(
          String.format(
              "%s Failed deserializing yaml to infraConfig for infrastructure with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, infrastructureEntity.getIdentifier(), infrastructureEntity.getAccountId(),
              infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier()),
          e);
      return false;
    }
    return true;
  }

  private ServiceDefinitionType mapInfraTypeToServiceDefinitionType(InfrastructureType type) {
    if (type == null) {
      return null;
    }
    // This might not be exactly what customer requires. We are doing it on best case scenario.
    switch (type) {
      case KUBERNETES_DIRECT:
      case KUBERNETES_GCP:
      case KUBERNETES_AZURE:
        return ServiceDefinitionType.KUBERNETES;
      case PDC:
      case SSH_WINRM_AWS:
      case SSH_WINRM_AZURE:
        return ServiceDefinitionType.SSH;
      case SERVERLESS_AWS_LAMBDA:
        return ServiceDefinitionType.SERVERLESS_AWS_LAMBDA;
      case ECS:
        return ServiceDefinitionType.ECS;
      case AZURE_WEB_APP:
        return ServiceDefinitionType.AZURE_WEBAPP;
      case CUSTOM_DEPLOYMENT:
        return ServiceDefinitionType.CUSTOM_DEPLOYMENT;
      default:
        return null;
    }
  }

  public String addDeploymentTypeToYaml(
      InfrastructureEntity infrastructureEntity, ServiceDefinitionType serviceDefinitionType) {
    try {
      YamlField infraEntityYamlField = YamlUtils.readTree(infrastructureEntity.getYaml());
      YamlField infrastructureDefinitionYamlField =
          infraEntityYamlField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
      ObjectNode infraDefinitionNode = (ObjectNode) infrastructureDefinitionYamlField.getNode().getCurrJsonNode();
      infraDefinitionNode.put(YamlTypes.DEPLOYMENT_TYPE, serviceDefinitionType.getYamlName());
      return YamlPipelineUtils.writeYamlString(infraEntityYamlField.getNode().getCurrJsonNode());
    } catch (Exception exception) {
      log.error(
          String.format(
              "%s Failed trying to add deployment type to yaml for infrastructure with identifier %s in account %s, org %s, project %s",
              DEBUG_LOG, infrastructureEntity.getIdentifier(), infrastructureEntity.getAccountId(),
              infrastructureEntity.getOrgIdentifier(), infrastructureEntity.getProjectIdentifier()),
          exception);
    }
    return null;
  }
}
