/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.generateFileIdentifier;
import static io.harness.ngmigration.utils.MigratorUtility.getYamlConfigFile;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET_MANAGER;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.InfraProvisionerSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.NgMigrationService;

import software.wings.beans.ARMInfrastructureProvisioner;
import software.wings.beans.ARMSourceType;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.InfrastructureProvisionerService;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class InfraProvisionerMigrationService extends NgMigrationService {
  @Inject InfrastructureProvisionerService infrastructureProvisionerService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new IllegalAccessError("Mapping not allowed for infra provisioner Service");
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> summaryByType =
        entities.stream()
            .map(entity -> (InfrastructureProvisioner) entity.getEntity())
            .collect(groupingBy(InfrastructureProvisioner::getInfrastructureProvisionerType, counting()));
    return InfraProvisionerSummary.builder().count(entities.size()).typeSummary(summaryByType).build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    InfrastructureProvisioner provisioner = (InfrastructureProvisioner) entity;
    String entityId = provisioner.getUuid();
    CgEntityId cgEntityId = CgEntityId.builder().type(INFRA_PROVISIONER).id(entityId).build();
    CgEntityNode entityNode =
        CgEntityNode.builder().id(entityId).type(INFRA_PROVISIONER).entityId(cgEntityId).entity(provisioner).build();
    Set<CgEntityId> children = new HashSet<>();
    if (provisioner instanceof TerraformInfrastructureProvisioner) {
      TerraformInfrastructureProvisioner terraformProvisioner = (TerraformInfrastructureProvisioner) provisioner;
      if (StringUtils.isNotBlank(terraformProvisioner.getSourceRepoSettingId())) {
        children.add(CgEntityId.builder().type(CONNECTOR).id(terraformProvisioner.getSourceRepoSettingId()).build());
      }
      if (StringUtils.isNotBlank(terraformProvisioner.getKmsId())) {
        children.add(CgEntityId.builder().type(SECRET_MANAGER).id(terraformProvisioner.getKmsId()).build());
      }
    } else if (provisioner instanceof ARMInfrastructureProvisioner) {
      ARMInfrastructureProvisioner armInfrastructureProvisioner = (ARMInfrastructureProvisioner) provisioner;
      if (null != armInfrastructureProvisioner.getSourceType()) {
        if (armInfrastructureProvisioner.getSourceType() == ARMSourceType.GIT) {
          children.add(CgEntityId.builder()
                           .type(CONNECTOR)
                           .id(armInfrastructureProvisioner.getGitFileConfig().getConnectorId())
                           .build());
        }
      }
    } else if (provisioner instanceof TerragruntInfrastructureProvisioner) {
      TerragruntInfrastructureProvisioner terragruntInfrastructureProvisioner =
          (TerragruntInfrastructureProvisioner) provisioner;
      if (StringUtils.isNotBlank(terragruntInfrastructureProvisioner.getSourceRepoSettingId())) {
        children.add(CgEntityId.builder()
                         .type(CONNECTOR)
                         .id(terragruntInfrastructureProvisioner.getSourceRepoSettingId())
                         .build());
      }
      if (StringUtils.isNotBlank(terragruntInfrastructureProvisioner.getSecretManagerId())) {
        children.add(CgEntityId.builder()
                         .type(SECRET_MANAGER)
                         .id(terragruntInfrastructureProvisioner.getSecretManagerId())
                         .build());
      }
    } else if (provisioner instanceof CloudFormationInfrastructureProvisioner) {
      CloudFormationInfrastructureProvisioner cfProvisioner = (CloudFormationInfrastructureProvisioner) provisioner;

      if (cfProvisioner.provisionByGit() && null != cfProvisioner.getGitFileConfig()
          && isNotEmpty(cfProvisioner.getGitFileConfig().getConnectorId())) {
        children.add(
            CgEntityId.builder().type(CONNECTOR).id(cfProvisioner.getGitFileConfig().getConnectorId()).build());
      }
    }

    return DiscoveryNode.builder().children(children).entityNode(entityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    try {
      return discover(infrastructureProvisionerService.get(appId, entityId));
    } catch (Exception e) {
      log.error("There was an error fetching infra provisioners", e);
      return null;
    }
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    throw new NotImplementedException("Infra Provisioner migrate is not supported");
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    List<NGYamlFile> result = new ArrayList<>();
    InfrastructureProvisioner provisioner = (InfrastructureProvisioner) entities.get(entityId).getEntity();
    if (provisioner instanceof ARMInfrastructureProvisioner) {
      ARMInfrastructureProvisioner armInfrastructureProvisioner = (ARMInfrastructureProvisioner) provisioner;
      if (isNotEmpty(armInfrastructureProvisioner.getTemplateBody())) {
        byte[] fileContent = armInfrastructureProvisioner.getTemplateBody().getBytes(StandardCharsets.UTF_8);
        NGYamlFile yamlConfigFile = getYamlConfigFile(inputDTO, fileContent,
            generateFileIdentifier(
                "infraProvisioners/" + armInfrastructureProvisioner.getName(), inputDTO.getIdentifierCaseFormat()));
        if (null != yamlConfigFile) {
          result.add(yamlConfigFile);
        }
      }
    }

    return YamlGenerationDetails.builder().yamlFileList(result).build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
