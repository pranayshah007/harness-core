/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.USER_GROUP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.dto.UserGroupYamlDTO;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.usergroups.UserGroupClient;

import software.wings.beans.security.UserGroup;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.UserGroupService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class UserGroupMigrationService extends NgMigrationService {
  @Inject private UserGroupService userGroupService;
  @Inject private UserGroupClient userGroupClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    UserGroupDTO userGroupDTO = ((UserGroupYamlDTO) yamlFile.getYaml()).getUserGroupDTO();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(USER_GROUP.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(userGroupDTO.getOrgIdentifier())
        .projectIdentifier(userGroupDTO.getProjectIdentifier())
        .identifier(userGroupDTO.getIdentifier())
        .scope(MigratorMappingService.getScope(userGroupDTO.getOrgIdentifier(), userGroupDTO.getProjectIdentifier()))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(basicInfo.getAccountId(),
            userGroupDTO.getOrgIdentifier(), userGroupDTO.getProjectIdentifier(), userGroupDTO.getIdentifier()))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return new BaseSummary(0);
    }
    return new BaseSummary(entities.size());
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    UserGroup userGroup = (UserGroup) entity;
    String entityId = userGroup.getUuid();
    CgEntityId cgEntityId = CgEntityId.builder().type(USER_GROUP).id(entityId).build();
    CgEntityNode triggerNode =
        CgEntityNode.builder().id(entityId).type(USER_GROUP).entityId(cgEntityId).entity(userGroup).build();
    Set<CgEntityId> children = new HashSet<>();
    return DiscoveryNode.builder().children(children).entityNode(triggerNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(userGroupService.get(accountId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    UserGroupDTO userGroupDTO = ((UserGroupYamlDTO) yamlFile.getYaml()).getUserGroupDTO();
    userGroupDTO.setAccountIdentifier(inputDTO.getDestinationAccountIdentifier());
    try {
      Response<ResponseDTO<UserGroupDTO>> resp =
          ngClient
              .createUserGroup(inputDTO.getDestinationAuthToken(), userGroupDTO.getAccountIdentifier(),
                  userGroupDTO.getOrgIdentifier(), userGroupDTO.getProjectIdentifier(), userGroupDTO)
              .execute();
      if (resp.code() >= 200 && resp.code() < 300) {
        return MigrationImportSummaryDTO.builder().success(true).errors(Collections.emptyList()).build();
      }
      Map<String, Object> error = JsonUtils.asObject(
          resp.errorBody() != null ? resp.errorBody().string() : "{}", new TypeReference<Map<String, Object>>() {});
      log.error(String.format(
          "There was error creating the user group. Response from NG - %s with error body errorBody -  %s", resp,
          error));
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(
              ImportError.builder()
                  .message(error.containsKey("message") ? error.get("message").toString()
                                                        : "There was an error creating the user group")
                  .build()))
          .build();
    } catch (IOException e) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder().message(e.getLocalizedMessage()).build()))
          .build();
    }
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    UserGroup userGroup = (UserGroup) migrationContext.getEntities().get(entityId).getEntity();
    String name =
        MigratorUtility.generateName(migrationContext.getInputDTO().getOverrides(), entityId, userGroup.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(migrationContext.getInputDTO().getOverrides(),
        entityId, name, migrationContext.getInputDTO().getIdentifierCaseFormat());
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    UserGroupYamlDTO yamlDTO = UserGroupYamlDTO.builder()
                                   .userGroupDTO(UserGroupDTO.builder()
                                                     .identifier(identifier)
                                                     .name(name)
                                                     .description(userGroup.getDescription())
                                                     .users(userGroup.getMemberIds())
                                                     .accountIdentifier(userGroup.getAccountId())
                                                     .orgIdentifier(orgIdentifier)
                                                     .projectIdentifier(projectIdentifier)
                                                     .build())
                                   .build();

    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .filename(String.format("usergroup/%s.yaml", userGroup.getName()))
            .yaml(yamlDTO)
            .type(USER_GROUP)
            .ngEntityDetail(NgEntityDetail.builder().entityType(USER_GROUP).identifier(identifier).build())
            .cgBasicInfo(userGroup.getCgBasicInfo())
            .build();
    migrationContext.getMigratedEntities().putIfAbsent(entityId, ngYamlFile);
    return YamlGenerationDetails.builder().yamlFileList(Collections.singletonList(ngYamlFile)).build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      UserGroupDTO response = NGRestUtils.getResponse(userGroupClient.getUserGroup(ngEntityDetail.getIdentifier(),
          accountIdentifier, ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      return UserGroupYamlDTO.builder().userGroupDTO(response).build();
    } catch (Exception ex) {
      log.warn("Error when getting user group - ", ex);
    }
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
