/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.DUMMY_HEAD;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.DummyNode;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.service.NgMigrationService;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;

import java.util.HashSet;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.CDC)
public class DummyMigrationService extends NgMigrationService {
  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new NotImplementedException("Dummy Method not implemented");
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    throw new NotImplementedException("Dummy Method not implemented");
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    CgEntityId cgEntityId = CgEntityId.builder().type(DUMMY_HEAD).id(DUMMY_HEAD.name()).build();
    return DiscoveryNode.builder()
        .entityNode(CgEntityNode.builder()
                        .type(DUMMY_HEAD)
                        .id(DUMMY_HEAD.name())
                        .entityId(cgEntityId)
                        .entity(DummyNode.builder().name("HEAD").build())
                        .build())
        .children(new HashSet<>())
        .build();
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    return null;
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return false;
  }
}
