/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.ChangeSet;
import io.harness.gitsync.YamlGitConfigInfo;
import io.harness.gitsync.common.beans.GitToHarnessFileProcessingRequest;
import io.harness.gitsync.common.dtos.ChangeSetWithYamlStatusDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.dtos.GitSyncEntityDTO;
import io.harness.gitsync.common.service.GitEntityService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.ng.core.event.EntityToEntityProtoHelper;
import io.harness.utils.FilePathUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@Singleton
@Slf4j
public class GitChangeSetMapper {
  private ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
  @Inject GitEntityService gitEntityService;
  @Inject EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;

  public List<ChangeSetWithYamlStatusDTO> toChangeSetList(List<GitToHarnessFileProcessingRequest> fileContentsList,
      String accountId, List<YamlGitConfigDTO> yamlGitConfigDTOs, String changesetId, String branchName) {
    return emptyIfNull(fileContentsList)
        .stream()
        .map(fileProcessingRequest
            -> mapToChangeSet(fileProcessingRequest.getFileDetails(), accountId, fileProcessingRequest.getChangeType(),
                yamlGitConfigDTOs, changesetId, branchName))
        .filter(Objects::nonNull)
        .collect(toList());
  }

  private ChangeSetWithYamlStatusDTO mapToChangeSet(GitFileChangeDTO fileContent, String accountId,
      ChangeType changeType, List<YamlGitConfigDTO> yamlGitConfigDTOs, String changesetId, String branchName) {
    fileContent.setPath(FilePathUtils.updatePathWithForwardSlash(fileContent.getPath()));
    ChangeSet.Builder builder = ChangeSet.newBuilder()
                                    .setAccountId(accountId)
                                    .setChangeType(ChangeTypeMapper.toProto(changeType))
                                    .setYaml(fileContent.getContent())
                                    .setChangeSetId(changesetId)
                                    .setFilePath(fileContent.getPath());
    if (isNotBlank(fileContent.getObjectId())) {
      builder.setObjectId(StringValue.of(fileContent.getObjectId()));
    }
    EntityReference entityReference = null;
    if (changeType == ChangeType.DELETE) {
      final Optional<EntityDetailProtoDTO> entityDetailDtoOptional = getEntityDetailFromGitEntitiesCollection(
          accountId, fileContent.getPath(), yamlGitConfigDTOs.get(0).getRepo(), branchName);
      if (entityDetailDtoOptional.isPresent()) {
        EntityDetailProtoDTO entityDetailDTO = entityDetailDtoOptional.get();
        builder.setEntityRefForDeletion(entityDetailDTO);
        builder.setEntityType(entityDetailDTO.getType());
        entityReference = entityDetailProtoToRestMapper.createEntityDetailDTO(entityDetailDTO).getEntityRef();
      } else {
        log.error("No git sync entity exists for the file path %s, in repoUrl %s and branch %s", fileContent.getPath(),
            yamlGitConfigDTOs.get(0).getRepo(), branchName);
        return ChangeSetWithYamlStatusDTO.builder()
            .changeSet(builder.build())
            .yamlInputErrorType(ChangeSetWithYamlStatusDTO.YamlInputErrorType.ENTITY_NOT_FOUND)
            .build();
      }
    } else {
      if (changeType == ChangeType.RENAME) {
        builder.setPrevFilePath(fileContent.getPrevFilePath());
      }

      try {
        EntityType entityType = GitSyncUtils.getEntityTypeFromYaml(fileContent.getContent());
        builder.setEntityType(EntityToEntityProtoHelper.getEntityTypeFromProto(entityType));
      } catch (Exception ex) {
        log.error("Unknown entity type encountered in file {}", fileContent.getPath(), ex);
        return ChangeSetWithYamlStatusDTO.builder()
            .changeSet(builder.build())
            .yamlInputErrorType(ChangeSetWithYamlStatusDTO.YamlInputErrorType.INVALID_ENTITY_TYPE)
            .build();
      }
    }

    return setYamlGitConfigInfoInChangeset(
        fileContent, accountId, yamlGitConfigDTOs, builder, changeType, entityReference);
  }

  private Optional<EntityDetailProtoDTO> getEntityDetailFromGitEntitiesCollection(
      String accountId, String path, String repo, String branchName) {
    Optional<GitSyncEntityDTO> gitSyncEntityDtoOptional = gitEntityService.get(accountId, path, repo, branchName);
    if (!gitSyncEntityDtoOptional.isPresent()) {
      return Optional.empty();
    }
    GitSyncEntityDTO gitSyncEntityDTO = gitSyncEntityDtoOptional.get();
    final EntityReference entityReference = gitSyncEntityDTO.getEntityReference();
    final EntityDetail entityDetail =
        EntityDetail.builder().entityRef(entityReference).type(gitSyncEntityDTO.getEntityType()).build();
    return Optional.of(entityDetailRestToProtoMapper.createEntityDetailDTO(entityDetail));
  }

  private ChangeSetWithYamlStatusDTO setYamlGitConfigInfoInChangeset(GitFileChangeDTO fileContent, String accountId,
      List<YamlGitConfigDTO> yamlGitConfigDTOs, ChangeSet.Builder builder, ChangeType changeType,
      EntityReference entityReference) {
    String orgIdentifier;
    String projectIdentifier;
    if (changeType == ChangeType.DELETE) {
      orgIdentifier = entityReference.getOrgIdentifier();
      projectIdentifier = entityReference.getProjectIdentifier();
    } else {
      try {
        final JsonNode jsonNode = convertYamlToJsonNode(fileContent.getContent());
        projectIdentifier = getKeyInNode(jsonNode, NGCommonEntityConstants.PROJECT_KEY);
        orgIdentifier = getKeyInNode(jsonNode, NGCommonEntityConstants.ORG_KEY);
      } catch (Exception e) {
        log.error(
            "Ill formed yaml found. Filepath: [{}], Content[{}]", fileContent.getPath(), fileContent.getContent(), e);
        return ChangeSetWithYamlStatusDTO.builder()
            .changeSet(builder.build())
            .yamlInputErrorType(ChangeSetWithYamlStatusDTO.YamlInputErrorType.PROJECT_ORG_IDENTIFIER_MISSING)
            .build();
      }
    }

    final Optional<YamlGitConfigDTO> yamlGitConfigDTO =
        getYamlGitConfigDTO(yamlGitConfigDTOs, orgIdentifier, projectIdentifier);

    if (!yamlGitConfigDTO.isPresent()) {
      return ChangeSetWithYamlStatusDTO.builder()
          .changeSet(builder.build())
          .yamlInputErrorType(ChangeSetWithYamlStatusDTO.YamlInputErrorType.YAML_FROM_NOT_GIT_SYNCED_PROJECT)
          .build();
    } else {
      YamlGitConfigDTO ygc = yamlGitConfigDTO.get();
      final YamlGitConfigInfo.Builder yamlGitConfigBuilder =
          YamlGitConfigInfo.newBuilder().setAccountId(accountId).setYamlGitConfigId(ygc.getIdentifier());
      if (isNotEmpty(projectIdentifier)) {
        yamlGitConfigBuilder.setYamlGitConfigProjectIdentifier(StringValue.of(projectIdentifier));
      }
      if (isNotEmpty(orgIdentifier)) {
        yamlGitConfigBuilder.setYamlGitConfigOrgIdentifier(StringValue.of(orgIdentifier));
      }
      ChangeSet updatedChangeSet = builder.setYamlGitConfigInfo(yamlGitConfigBuilder.build()).build();
      return ChangeSetWithYamlStatusDTO.builder()
          .changeSet(updatedChangeSet)
          .yamlInputErrorType(ChangeSetWithYamlStatusDTO.YamlInputErrorType.NIL)
          .build();
    }
  }

  @VisibleForTesting
  static String getKeyInNode(JsonNode jsonNode, String key) {
    return jsonNode.fields().next().getValue().get(key).asText();
  }

  @VisibleForTesting
  JsonNode convertYamlToJsonNode(String yaml) throws IOException {
    return objectMapper.readTree(yaml);
  }

  private Optional<YamlGitConfigDTO> getYamlGitConfigDTO(
      List<YamlGitConfigDTO> yamlGitConfigDTOs, String orgIdentifier, String projectIdentifier) {
    // If we don't have any yaml git config for the scope we skip changeset
    return yamlGitConfigDTOs.stream()
        .map(ygc -> {
          boolean matches = true;
          if (isNotEmpty(ygc.getProjectIdentifier())) {
            matches = ygc.getProjectIdentifier().equals(projectIdentifier);
          }
          if (!matches) {
            return null;
          }
          if (isNotEmpty(ygc.getOrganizationIdentifier())) {
            matches = ygc.getOrganizationIdentifier().equals(orgIdentifier);
          }
          if (!matches) {
            return null;
          }
          return ygc;
        })
        .filter(Objects::nonNull)
        .findFirst();
  }
}
