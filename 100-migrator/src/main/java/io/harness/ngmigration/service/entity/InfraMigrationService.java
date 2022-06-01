/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.api.CloudProviderType.KUBERNETES_CLUSTER;
import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.InfrastructureDefinitionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class InfraMigrationService extends NgMigrationService {
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private MigratorExpressionUtils migratorExpressionUtils;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new IllegalAccessError("Mapping not allowed for Infrastructure");
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    InfrastructureDefinition infra = (InfrastructureDefinition) entity;
    String entityId = infra.getUuid();
    CgEntityId infraEntityId = CgEntityId.builder().type(NGMigrationEntityType.INFRA).id(entityId).build();
    CgEntityNode infraNode = CgEntityNode.builder()
                                 .id(entityId)
                                 .type(NGMigrationEntityType.INFRA)
                                 .entityId(infraEntityId)
                                 .entity(infra)
                                 .build();

    Set<CgEntityId> children = new HashSet<>();
    children.add(CgEntityId.builder().id(infra.getInfrastructure().getCloudProviderId()).type(CONNECTOR).build());
    return DiscoveryNode.builder().children(children).entityNode(infraNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(infrastructureDefinitionService.get(appId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    InfrastructureDefinition infra = (InfrastructureDefinition) entity;
    if (infra.getCloudProviderType() != KUBERNETES_CLUSTER) {
      return NGMigrationStatus.builder()
          .status(false)
          .reasons(
              Collections.singletonList(String.format("%s infra with cloud provider %s is not supported with migration",
                  infra.getName(), infra.getCloudProviderType())))
          .build();
    }
    if (!(infra.getInfrastructure() instanceof DirectKubernetesInfrastructure)) {
      return NGMigrationStatus.builder()
          .status(false)
          .reasons(Collections.singletonList(String.format(
              "Issue With %s infra. We currently support only Direct Infra with migration", infra.getName())))
          .build();
    }
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {}

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    return new ArrayList<>();
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return false;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    InfrastructureDefinition infra = (InfrastructureDefinition) entities.get(entityId).getEntity();
    return BaseEntityInput.builder()
        .migrationStatus(MigratorInputType.CREATE_NEW)
        .identifier(BaseInputDefinition.buildIdentifier(MigratorUtility.generateIdentifier(infra.getName())))
        .name(BaseInputDefinition.buildName(infra.getName()))
        .spec(null)
        .build();
  }

  public InfrastructureDef getInfraDef(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entities.get(entityId).getEntity();
    migratorExpressionUtils.render(infrastructureDefinition);

    if (infrastructureDefinition.getCloudProviderType() != KUBERNETES_CLUSTER) {
      throw new UnsupportedOperationException("Only support K8s deployment");
    }
    if (!(infrastructureDefinition.getInfrastructure() instanceof DirectKubernetesInfrastructure)) {
      throw new UnsupportedOperationException("Only support Direct Infra");
    }
    DirectKubernetesInfrastructure k8sInfra =
        (DirectKubernetesInfrastructure) infrastructureDefinition.getInfrastructure();

    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder()
                                 .type(CONNECTOR)
                                 .id(infrastructureDefinition.getInfrastructure().getCloudProviderId())
                                 .build());
    // TODO: Fix Release Name. release-${infra.kubernetes.infraId} -> release-<+INFRA_KEY>
    return InfrastructureDef.builder()
        .type(InfrastructureType.KUBERNETES_DIRECT)
        .spec(K8SDirectInfrastructure.builder()
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .namespace(ParameterField.createValueField(k8sInfra.getNamespace()))
                  .releaseName(ParameterField.createValueField(k8sInfra.getReleaseName()))
                  .build())
        .build();
  }
}
