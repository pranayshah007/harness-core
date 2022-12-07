/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import io.harness.beans.EncryptedData;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.AccountSummary;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Account;
import software.wings.beans.template.Template;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountMigrationService extends NgMigrationService {
  @Inject private AccountService accountService;
  @Inject private AppService appService;
  @Inject private HPersistence hPersistence;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject TemplateService templateService;

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

    List<Template> templates = templateService.listAccountLevelTemplates(accountId);
    if (EmptyPredicate.isNotEmpty(templates)) {
      children.addAll(
          templates.stream()
              .map(template -> CgEntityId.builder().id(template.getUuid()).type(NGMigrationEntityType.TEMPLATE).build())
              .collect(Collectors.toList()));
    }

    try {
      List<EncryptedData> encryptedDataList =
          secretManager
              .listSecrets(accountId,
                  PageRequestBuilder.aPageRequest()
                      .addFilter(EncryptedData.ACCOUNT_ID_KEY, Operator.EQ, accountId)
                      .withLimit("UNLIMITED")
                      .build(),
                  null, null, true, false)
              .getResponse();
      if (EmptyPredicate.isNotEmpty(encryptedDataList)) {
        children.addAll(
            encryptedDataList.stream()
                .map(secret -> CgEntityId.builder().id(secret.getUuid()).type(NGMigrationEntityType.SECRET).build())
                .collect(Collectors.toList()));
      }
    } catch (Exception e) {
      log.error("There was error listing secrets", e);
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
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
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
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Account account = (Account) entities.get(0).getEntity();
    return new AccountSummary(entities.size(), account.getAccountName());
  }
}
