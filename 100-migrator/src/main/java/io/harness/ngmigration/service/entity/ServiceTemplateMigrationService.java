/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.SERVICE_TEMPLATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.NgMigrationService;

import software.wings.beans.ServiceTemplate;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.ServiceTemplateService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ServiceTemplateMigrationService extends NgMigrationService {
  @Inject private ServiceTemplateService serviceTemplateService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    ServiceTemplate serviceTemplate = (ServiceTemplate) entity;
    String entityId = serviceTemplate.getUuid();
    CgEntityId variableEntityId = CgEntityId.builder().type(SERVICE_TEMPLATE).id(entityId).build();
    CgEntityNode variableNode = CgEntityNode.builder()
                                    .id(entityId)
                                    .type(SERVICE_TEMPLATE)
                                    .entityId(variableEntityId)
                                    .entity(serviceTemplate)
                                    .build();
    return DiscoveryNode.builder().children(new HashSet<>()).entityNode(variableNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceTemplateService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return null;
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    return YamlGenerationDetails.builder().yamlFileList(new ArrayList<>()).build();
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
