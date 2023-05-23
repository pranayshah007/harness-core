/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverridesGroupSettingsUpdateResult;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideSettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideSettingsUpdateResponseDTO.ServiceOverrideSettingsUpdateResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.SingleServiceOverrideYamlUpdateResponse;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingRequestDTO.SettingRequestDTOBuilder;
import io.harness.ngsettings.dto.SettingResponseDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;
import retrofit2.Call;

@Slf4j
public class ServiceOverrideV2SettingsUpdateServiceImpl implements ServiceOverrideV2SettingsUpdateService {
  @Inject MongoTemplate mongoTemplate;
  private static final String DEBUG_LINE = "[ServiceOverrideV2SettingsUpdateServiceImpl]: ";

  @Override
  @NonNull
  public ServiceOverrideSettingsUpdateResponseDTO settingsUpdateToV2(
      @NonNull String accountId, String orgId, String projectId, boolean migrateChildren, boolean isRevert) {
    ServiceOverrideSettingsUpdateResponseDTOBuilder responseDTOBuilder =
        ServiceOverrideSettingsUpdateResponseDTO.builder().accountId(accountId);
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelResponseDTOs = new ArrayList<>();
    List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelResponseDTOs = new ArrayList<>();
    if (isNotEmpty(projectId)) {
      log.info(String.format(
          DEBUG_LINE + "Starting project level settings update for orgId: [%s], project :[%s]", orgId, projectId));
      projectLevelResponseDTOs = List.of(doProjectLevelSettingsUpdate(accountId, orgId, projectId, isRevert));
      log.info(String.format(
          DEBUG_LINE + "Successfully finished project level settings update for orgId: [%s], project :[%s]", orgId,
          projectId));
      ServiceOverrideSettingsUpdateResponseDTO migrationResponseDTO =
          responseDTOBuilder.projectLevelUpdateInfo(projectLevelResponseDTOs).build();
      migrationResponseDTO.setSuccessful(isOverallSuccessful(migrationResponseDTO));
      return migrationResponseDTO;
    }

    if (isNotEmpty(orgId)) {
      log.info(String.format(DEBUG_LINE + "Starting org level settings update for orgId: [%s]", orgId));
      OrgLevelOverrideV2SettingsUpdateResponseDTO orgLevelResponseDTO =
          doOrgLevelSettingsUpdate(accountId, orgId, isRevert);
      if (migrateChildren) {
        projectLevelResponseDTOs = doChildProjectsSettingsUpdate(accountId, orgId, isRevert);
      }
      log.info(String.format(DEBUG_LINE + "Successfully finished org level settings update for orgId: [%s]", orgId));

      ServiceOverrideSettingsUpdateResponseDTO migrationResponseDTO =
          responseDTOBuilder.orgLevelUpdateInfo(List.of(orgLevelResponseDTO))
              .projectLevelUpdateInfo(projectLevelResponseDTOs)
              .build();
      migrationResponseDTO.setSuccessful(isOverallSuccessful(migrationResponseDTO));

      return migrationResponseDTO;
    }

    log.info(String.format(DEBUG_LINE + "Starting account level settings update for orgId: [%s]", accountId));

    AccountLevelOverrideV2SettingsUpdateResponseDTO accountLevelResponseDTO =
        doAccountLevelSettingsUpdate(accountId, isRevert);
    if (migrateChildren) {
      orgLevelResponseDTOs = doChildLevelOrgSettingsUpdate(accountId, isRevert);
      List<String> orgIdsInAccount = orgLevelResponseDTOs.stream()
                                         .map(OrgLevelOverrideV2SettingsUpdateResponseDTO::getOrgIdentifier)
                                         .collect(Collectors.toList());
      for (String localOrgId : orgIdsInAccount) {
        projectLevelResponseDTOs.addAll(doChildProjectsSettingsUpdate(accountId, localOrgId, isRevert));
      }
    }
    log.info(
        String.format(DEBUG_LINE + "Successfully finished account level settings update for account: [%s]", accountId));
    ServiceOverrideSettingsUpdateResponseDTO migrationResponseDTO =
        responseDTOBuilder.accountLevelUpdateInfo(accountLevelResponseDTO)
            .orgLevelUpdateInfo(orgLevelResponseDTOs)
            .projectLevelUpdateInfo(projectLevelResponseDTOs)
            .build();
    migrationResponseDTO.setSuccessful(isOverallSuccessful(migrationResponseDTO));
    return migrationResponseDTO;
  }

  @NonNull
  private List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> doChildProjectsSettingsUpdate(
      String accountId, String orgId, boolean isRevert) {
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelResponseDTOS = new ArrayList<>();

    try {
      Criteria criteria = new Criteria()
                              .and(ProjectKeys.accountIdentifier)
                              .is(accountId)
                              .and(ProjectKeys.orgIdentifier)
                              .is(orgId)
                              .and(ProjectKeys.deleted)
                              .is(false);
      Query query = new Query(criteria);
      query.fields().include(ProjectKeys.identifier);

      List<String> projectIds =
          mongoTemplate.find(query, Project.class).stream().map(Project::getIdentifier).collect(Collectors.toList());
      for (String projectId : projectIds) {
        ProjectLevelOverrideV2SettingsUpdateResponseDTO projectLevelResponseDTO =
            doProjectLevelSettingsUpdate(accountId, orgId, projectId, isRevert);
        projectLevelResponseDTOS.add(projectLevelResponseDTO);
      }
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Settings update failed for children projects of org: [%s]", orgId), e);
    }

    return projectLevelResponseDTOS;
  }

  @NonNull
  private List<OrgLevelOverrideV2SettingsUpdateResponseDTO> doChildLevelOrgSettingsUpdate(
      String accountId, boolean isRevert) {
    List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelResponseDTOS = new ArrayList<>();

    try {
      Criteria criteria =
          new Criteria().and(OrganizationKeys.accountIdentifier).is(accountId).and(OrganizationKeys.deleted).is(false);

      Query query = new Query(criteria);
      query.fields().include(OrganizationKeys.identifier);
      List<String> orgIds = mongoTemplate.find(query, Organization.class)
                                .stream()
                                .map(Organization::getIdentifier)
                                .collect(Collectors.toList());

      for (String orgId : orgIds) {
        OrgLevelOverrideV2SettingsUpdateResponseDTO orgLevelResponseDTO =
            doOrgLevelSettingsUpdate(accountId, orgId, isRevert);
        orgLevelResponseDTOS.add(orgLevelResponseDTO);
      }
    } catch (Exception e) {
      log.error(
          String.format(DEBUG_LINE + "Settings Update failed for children organizations of account: [%s]", accountId),
          e);
    }
    return orgLevelResponseDTOS;
  }

  @NotNull
  private static Criteria getCriteriaForProjectServiceOverrides(
      String accountId, String orgId, String projectId, boolean isRevert) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(NGServiceOverridesEntityKeys.projectIdentifier)
                            .is(projectId);

    if (isRevert) {
      criteria.and("yaml_deprecated").exists(true);
    }
    return criteria;
  }

  @NotNull
  private static Criteria getCriteriaForOrgServiceOverrides(String accountId, String orgId, boolean isRevert) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId);

    if (isRevert) {
      criteria.and("yaml_deprecated").exists(true);
    }
    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).is(null)));
  }

  @NotNull
  private static Criteria getCriteriaForAccountServiceOverrides(String accountId, boolean isRevert) {
    Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.accountId).is(accountId);
    if (isRevert) {
      criteria.and("yaml_deprecated").exists(true);
    }
    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).is(null)),
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).is(null)));
  }

  private boolean isOverallSuccessful(ServiceOverrideSettingsUpdateResponseDTO responseDTO) {
    boolean isSuccessful = true;

    if (isNotEmpty(responseDTO.getProjectLevelUpdateInfo())) {
      isSuccessful = checkSuccessInProjects(responseDTO);
    }

    if (isNotEmpty(responseDTO.getOrgLevelUpdateInfo())) {
      isSuccessful = isSuccessful && checkSuccessInOrgs(responseDTO);
    }

    if (responseDTO.getAccountLevelUpdateInfo() != null) {
      isSuccessful = isSuccessful && checkSuccessInAccount(responseDTO);
    }

    return isSuccessful;
  }

  private static boolean checkSuccessInAccount(ServiceOverrideSettingsUpdateResponseDTO responseDTO) {
    return responseDTO.getAccountLevelUpdateInfo().isSettingsUpdateSuccessFul();
  }

  private static boolean checkSuccessInOrgs(ServiceOverrideSettingsUpdateResponseDTO responseDTO) {
    return (responseDTO.getOrgLevelUpdateInfo().stream().allMatch(
        OrgLevelOverrideV2SettingsUpdateResponseDTO::isSettingsUpdateSuccessFul));
  }

  private static boolean checkSuccessInProjects(ServiceOverrideSettingsUpdateResponseDTO responseDTO) {
    return (responseDTO.getProjectLevelUpdateInfo().stream().allMatch(
        ProjectLevelOverrideV2SettingsUpdateResponseDTO::isSettingsUpdateSuccessFul));
  }

  private Call<ResponseDTO<List<SettingResponseDTO>>> getResponseDTOCall(
      String accountId, String orgId, String projectId, boolean isRevert) {
    List<SettingRequestDTO> settingRequestDTOList = new ArrayList<>();
    SettingRequestDTOBuilder settingRequestDTOBuilder = SettingRequestDTO.builder()
                                                            .identifier("service_override_v2")
                                                            .updateType(SettingUpdateType.UPDATE)
                                                            .allowOverrides(Boolean.FALSE);

    if (isRevert) {
      settingRequestDTOBuilder.value("false");
    } else {
      settingRequestDTOBuilder.value("true");
    }

    settingRequestDTOList.add(settingRequestDTOBuilder.build());
    // TODO: Settings Update call to be added
    return null;
  }

  @NonNull
  private ProjectLevelOverrideV2SettingsUpdateResponseDTO doProjectLevelSettingsUpdate(
      String accountId, String orgId, String projectId, boolean isRevert) {
    boolean isSettingsUpdateSuccessful = true;
    Call<ResponseDTO<List<SettingResponseDTO>>> responseDTOCall = null;
    OverridesGroupSettingsUpdateResult overrideResult = null;
    try {
      responseDTOCall = getResponseDTOCall(accountId, orgId, projectId, isRevert);
      Criteria criteria = getCriteriaForProjectServiceOverrides(accountId, orgId, projectId, isRevert);
      overrideResult = doLevelScopedOverridesYamlFieldUpdate(accountId, orgId, projectId, criteria, isRevert);
      isSettingsUpdateSuccessful = overrideResult.isSuccessFul();
    } catch (Exception e) {
      log.error(
          String.format(DEBUG_LINE + "Override settings update failed for project with orgId: [%s], project :[%s]",
              orgId, projectId),
          e);
      isSettingsUpdateSuccessful = false;
    }

    return ProjectLevelOverrideV2SettingsUpdateResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .isSettingsUpdateSuccessFul(isSettingsUpdateSuccessful)
        .settingResponseDTO(responseDTOCall)
        .overrideResult(overrideResult)
        .build();
  }

  @NonNull
  private OrgLevelOverrideV2SettingsUpdateResponseDTO doOrgLevelSettingsUpdate(
      String accountId, String orgId, boolean isRevert) {
    boolean isSettingsUpdateSuccessful = true;
    Call<ResponseDTO<List<SettingResponseDTO>>> responseDTOCall = null;
    OverridesGroupSettingsUpdateResult overrideResult = null;
    try {
      responseDTOCall = getResponseDTOCall(accountId, orgId, null, isRevert);
      Criteria criteria = getCriteriaForOrgServiceOverrides(accountId, orgId, isRevert);
      overrideResult = doLevelScopedOverridesYamlFieldUpdate(accountId, orgId, null, criteria, isRevert);
      isSettingsUpdateSuccessful = overrideResult.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Override settings update failed for project with orgId: [%s]", orgId), e);
      isSettingsUpdateSuccessful = false;
    }

    return OrgLevelOverrideV2SettingsUpdateResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .isSettingsUpdateSuccessFul(isSettingsUpdateSuccessful)
        .settingResponseDTO(responseDTOCall)
        .overrideResult(overrideResult)
        .build();
  }

  @NonNull
  private AccountLevelOverrideV2SettingsUpdateResponseDTO doAccountLevelSettingsUpdate(
      String accountId, boolean isRevert) {
    boolean isSettingsUpdateSuccessful = true;
    Call<ResponseDTO<List<SettingResponseDTO>>> responseDTOCall = null;
    OverridesGroupSettingsUpdateResult overrideResult = null;
    try {
      responseDTOCall = getResponseDTOCall(accountId, null, null, isRevert);
      Criteria criteria = getCriteriaForAccountServiceOverrides(accountId, isRevert);
      overrideResult = doLevelScopedOverridesYamlFieldUpdate(accountId, null, null, criteria, isRevert);
      isSettingsUpdateSuccessful = overrideResult.isSuccessFul();
    } catch (Exception e) {
      isSettingsUpdateSuccessful = false;
      log.error(
          String.format(DEBUG_LINE + "Override settings update failed for project with accountId: [%s]", accountId), e);
    }

    return AccountLevelOverrideV2SettingsUpdateResponseDTO.builder()
        .accountId(accountId)
        .isSettingsUpdateSuccessFul(isSettingsUpdateSuccessful)
        .settingResponseDTO(responseDTOCall)
        .overrideResult(overrideResult)
        .build();
  }

  private OverridesGroupSettingsUpdateResult doLevelScopedOverridesYamlFieldUpdate(
      String accountId, String orgId, String projectId, Criteria criteria, boolean isRevert) {
    long yamlUpdatedServiceOverridesCount = 0L;
    long totalServiceOverride = 0L;
    boolean isSuccessFul = true;

    try {
      Query queryForEntitiesToBeUpdated = new Query(criteria);
      totalServiceOverride = mongoTemplate.count(queryForEntitiesToBeUpdated, NGServiceOverridesEntity.class);
      if (totalServiceOverride > 0L) {
        try (CloseableIterator<NGServiceOverridesEntity> iterator =
                 mongoTemplate.stream(queryForEntitiesToBeUpdated, NGServiceOverridesEntity.class)) {
          while (iterator.hasNext()) {
            NGServiceOverridesEntity overridesEntity = iterator.next();
            SingleServiceOverrideYamlUpdateResponse singleMigrationResponse =
                doYamlFieldUpdateForSingleOverrideEntity(overridesEntity, isRevert);
            if (!singleMigrationResponse.isSuccessful()) {
              isSuccessFul = false;
            } else {
              yamlUpdatedServiceOverridesCount++;
            }
          }
        }
        if (totalServiceOverride != yamlUpdatedServiceOverridesCount) {
          isSuccessFul = false;
          log.error(String.format(DEBUG_LINE
                  + "Yaml updated count [%d] and Target count [%d] does not match, projectId: [%s], orgId: [%s], accountId: [%s]",
              yamlUpdatedServiceOverridesCount, totalServiceOverride, projectId, orgId, accountId));
        }
      }

    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE
                        + "Yaml update failed for scoped defined by projectId: [%s], orgId: [%s], accountId: [%s]",
                    projectId, orgId, accountId),
          e);
      isSuccessFul = false;
    }

    return OverridesGroupSettingsUpdateResult.builder()
        .isSuccessFul(isSuccessFul)
        .totalServiceOverrideCount(totalServiceOverride)
        .yamlUpdatedServiceOverridesCount(yamlUpdatedServiceOverridesCount)
        .build();
  }

  @NonNull
  private SingleServiceOverrideYamlUpdateResponse doYamlFieldUpdateForSingleOverrideEntity(
      NGServiceOverridesEntity overridesEntity, boolean isRevert) {
    try {
      Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.id).is(overridesEntity.getId());
      Query query = new Query(criteria);
      Update update = new Update();
      if (isRevert) {
        update.rename("yaml_deprecated", NGServiceOverridesEntityKeys.yaml);
      } else {
        update.rename(NGServiceOverridesEntityKeys.yaml, "yaml_deprecated");
      }
      mongoTemplate.updateFirst(query, update, NGServiceOverridesEntity.class);
      return SingleServiceOverrideYamlUpdateResponse.builder()
          .isSuccessful(true)
          .serviceRef(overridesEntity.getServiceRef())
          .envRef(overridesEntity.getEnvironmentRef())
          .projectId(overridesEntity.getProjectIdentifier())
          .orgId(overridesEntity.getOrgIdentifier())
          .accountId(overridesEntity.getAccountId())
          .build();
    } catch (Exception e) {
      log.error(
          String.format(
              "Service Override yaml update failed for override with serviceRef: [%s], environmentRef: [%s], projectId: [%s], orgId: [%s]",
              overridesEntity.getServiceRef(), overridesEntity.getEnvironmentRef(),
              overridesEntity.getProjectIdentifier(), overridesEntity.getOrgIdentifier()),
          e);

      return SingleServiceOverrideYamlUpdateResponse.builder()
          .isSuccessful(false)
          .serviceRef(overridesEntity.getServiceRef())
          .envRef(overridesEntity.getEnvironmentRef())
          .projectId(overridesEntity.getProjectIdentifier())
          .orgId(overridesEntity.getOrgIdentifier())
          .accountId(overridesEntity.getAccountId())
          .build();
    }
  }
}
