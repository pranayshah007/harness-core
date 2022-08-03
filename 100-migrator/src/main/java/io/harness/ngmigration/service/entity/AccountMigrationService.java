/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AccountMigrationService extends NgMigrationService {
  @Inject private AccountService accountService;
  @Inject private AppService appService;
  @Inject private HPersistence hPersistence;
  @Inject private SettingsService settingsService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    Account account = (Account) entity;
    String accountId = account.getUuid();
    Set<CgEntityId> children = new HashSet<>();

    List<String> appIds = appService.getAppIdsByAccountId(accountId);
    if (EmptyPredicate.isNotEmpty(appIds)) {
      children.addAll(appIds.stream()
                          .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.APPLICATION).build())
                          .collect(Collectors.toList()));
    }

    List<String> settingIds = settingsService.getSettingIdsForAccount(accountId);
    if (EmptyPredicate.isNotEmpty(settingIds)) {
      children.addAll(settingIds.stream()
                          .map(id -> CgEntityId.builder().id(id).type(NGMigrationEntityType.CONNECTOR).build())
                          .collect(Collectors.toList()));
    }

    return DiscoveryNode.builder()
        .entityNode(CgEntityNode.builder()
                        .id(null)
                        .type(NGMigrationEntityType.ACCOUNT)
                        .entity(entity)
                        .entityId(CgEntityId.builder().type(NGMigrationEntityType.ACCOUNT).id(accountId).build())
                        .build())
        .children(children)
        .build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(accountService.get(entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {
    // Nothing to do here for accounts for now.
    // We can probably just import the connectors on account or project or org level.
  }

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
    return null;
  }
}
