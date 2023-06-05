/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverrideV2SettingsUpdateResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OverrideV2SettingsUpdateResponseDTO.OverrideV2SettingsUpdateResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideV2SettingsUpdateResponseDTO;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingRequestDTO.SettingRequestDTOBuilder;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
public class ServiceOverrideV2SettingsUpdateServiceImpl implements ServiceOverrideV2SettingsUpdateService {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private NGSettingsClient ngSettingsClient;
  private static final String DEBUG_LINE = "[ServiceOverrideV2SettingsUpdateServiceImpl]: ";

  @Override
  @NonNull
  public OverrideV2SettingsUpdateResponseDTO settingsUpdateToV2(
      @NonNull String accountId, String orgId, String projectId, boolean updateChildren, boolean isRevert) {
    OverrideV2SettingsUpdateResponseDTOBuilder responseDTOBuilder =
        OverrideV2SettingsUpdateResponseDTO.builder().accountId(accountId);
    List<ProjectLevelOverrideV2SettingsUpdateResponseDTO> projectLevelResponseDTOs = new ArrayList<>();
    List<OrgLevelOverrideV2SettingsUpdateResponseDTO> orgLevelResponseDTOs = new ArrayList<>();
    if (isNotEmpty(projectId)) {
      log.info(String.format(
          DEBUG_LINE + "Starting project level settings update for orgId: [%s], project :[%s]", orgId, projectId));
      projectLevelResponseDTOs = List.of(doProjectLevelSettingsUpdate(accountId, orgId, projectId, isRevert));
      log.info(String.format(
          DEBUG_LINE + "Successfully finished project level settings update for orgId: [%s], project :[%s]", orgId,
          projectId));
      OverrideV2SettingsUpdateResponseDTO settingsUpdateResponseDTO =
          responseDTOBuilder.projectLevelUpdateInfo(projectLevelResponseDTOs).build();
      settingsUpdateResponseDTO.setSuccessful(isOverallSuccessful(settingsUpdateResponseDTO));
      return settingsUpdateResponseDTO;
    }

    if (isNotEmpty(orgId)) {
      log.info(String.format(DEBUG_LINE + "Starting org level settings update for orgId: [%s]", orgId));
      OrgLevelOverrideV2SettingsUpdateResponseDTO orgLevelResponseDTO =
          doOrgLevelSettingsUpdate(accountId, orgId, isRevert);
      if (updateChildren) {
        projectLevelResponseDTOs = doChildProjectsSettingsUpdate(accountId, orgId, isRevert);
      }
      log.info(String.format(DEBUG_LINE + "Successfully finished org level settings update for orgId: [%s]", orgId));

      OverrideV2SettingsUpdateResponseDTO settingsUpdateResponseDTO =
          responseDTOBuilder.orgLevelUpdateInfo(List.of(orgLevelResponseDTO))
              .projectLevelUpdateInfo(projectLevelResponseDTOs)
              .build();
      settingsUpdateResponseDTO.setSuccessful(isOverallSuccessful(settingsUpdateResponseDTO));

      return settingsUpdateResponseDTO;
    }

    log.info(String.format(DEBUG_LINE + "Starting account level settings update for accountId: [%s]", accountId));

    AccountLevelOverrideV2SettingsUpdateResponseDTO accountLevelResponseDTO =
        doAccountLevelSettingsUpdate(accountId, isRevert);
    if (updateChildren) {
      orgLevelResponseDTOs = doChildLevelOrgSettingsUpdate(accountId, isRevert);
      List<String> orgIdsInAccount = orgLevelResponseDTOs.stream()
                                         .map(OrgLevelOverrideV2SettingsUpdateResponseDTO::getOrgIdentifier)
                                         .collect(Collectors.toList());
      for (String localOrgId : orgIdsInAccount) {
        projectLevelResponseDTOs.addAll(doChildProjectsSettingsUpdate(accountId, localOrgId, isRevert));
      }
    }
    log.info(String.format(
        DEBUG_LINE + "Successfully finished account level settings update for accountId: [%s]", accountId));
    OverrideV2SettingsUpdateResponseDTO settingsUpdateResponseDTO =
        responseDTOBuilder.accountLevelUpdateInfo(accountLevelResponseDTO)
            .orgLevelUpdateInfo(orgLevelResponseDTOs)
            .projectLevelUpdateInfo(projectLevelResponseDTOs)
            .build();
    settingsUpdateResponseDTO.setSuccessful(isOverallSuccessful(settingsUpdateResponseDTO));
    return settingsUpdateResponseDTO;
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

  private boolean isOverallSuccessful(OverrideV2SettingsUpdateResponseDTO responseDTO) {
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

  private static boolean checkSuccessInAccount(OverrideV2SettingsUpdateResponseDTO responseDTO) {
    return responseDTO.getAccountLevelUpdateInfo().isSettingsUpdateSuccessFul();
  }

  private static boolean checkSuccessInOrgs(OverrideV2SettingsUpdateResponseDTO responseDTO) {
    return (responseDTO.getOrgLevelUpdateInfo().stream().allMatch(
        OrgLevelOverrideV2SettingsUpdateResponseDTO::isSettingsUpdateSuccessFul));
  }

  private static boolean checkSuccessInProjects(OverrideV2SettingsUpdateResponseDTO responseDTO) {
    return (responseDTO.getProjectLevelUpdateInfo().stream().allMatch(
        ProjectLevelOverrideV2SettingsUpdateResponseDTO::isSettingsUpdateSuccessFul));
  }

  private List<SettingResponseDTO> updateOverrideSettings(
      String accountId, String orgId, String projectId, boolean isRevert) {
    List<SettingRequestDTO> settingRequestDTOList = new ArrayList<>();
    SettingRequestDTOBuilder settingRequestDTOBuilder = SettingRequestDTO.builder()
                                                            .identifier("service_override_v2")
                                                            .updateType(SettingUpdateType.UPDATE)
                                                            .allowOverrides(Boolean.TRUE);

    if (isRevert) {
      settingRequestDTOBuilder.value("false");
    } else {
      settingRequestDTOBuilder.value("true");
    }

    settingRequestDTOList.add(settingRequestDTOBuilder.build());
    List<SettingResponseDTO> settingResponseDTOList =
        NGRestUtils.getResponse(ngSettingsClient.updateSettings(accountId, orgId, projectId, settingRequestDTOList));
    return settingResponseDTOList;
  }

  @NonNull
  private ProjectLevelOverrideV2SettingsUpdateResponseDTO doProjectLevelSettingsUpdate(
      String accountId, String orgId, String projectId, boolean isRevert) {
    boolean isSettingsUpdateSuccessful = true;
    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    try {
      settingResponseDTOList = updateOverrideSettings(accountId, orgId, projectId, isRevert);
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE
                        + "Settings update failed for project with accountId: [%s], orgId: [%s], projectId: [%s]",
                    accountId, orgId, projectId),
          e);
      isSettingsUpdateSuccessful = false;
    }

    return ProjectLevelOverrideV2SettingsUpdateResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .isSettingsUpdateSuccessFul(isSettingsUpdateSuccessful)
        .settingResponseDTO(settingResponseDTOList)
        .build();
  }

  @NonNull
  private OrgLevelOverrideV2SettingsUpdateResponseDTO doOrgLevelSettingsUpdate(
      String accountId, String orgId, boolean isRevert) {
    boolean isSettingsUpdateSuccessful = true;
    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    try {
      settingResponseDTOList = updateOverrideSettings(accountId, orgId, null, isRevert);
    } catch (Exception e) {
      log.error(String.format(
                    DEBUG_LINE + "Settings update failed for org with accountId: [%s], orgId: [%s]", accountId, orgId),
          e);
      isSettingsUpdateSuccessful = false;
    }

    return OrgLevelOverrideV2SettingsUpdateResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .isSettingsUpdateSuccessFul(isSettingsUpdateSuccessful)
        .settingResponseDTO(settingResponseDTOList)
        .build();
  }

  @NonNull
  private AccountLevelOverrideV2SettingsUpdateResponseDTO doAccountLevelSettingsUpdate(
      String accountId, boolean isRevert) {
    boolean isSettingsUpdateSuccessful = true;
    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    try {
      settingResponseDTOList = updateOverrideSettings(accountId, null, null, isRevert);
    } catch (Exception e) {
      isSettingsUpdateSuccessful = false;
      log.error(String.format(DEBUG_LINE + "Settings update failed for account with accountId: [%s]", accountId), e);
    }

    return AccountLevelOverrideV2SettingsUpdateResponseDTO.builder()
        .accountId(accountId)
        .isSettingsUpdateSuccessFul(isSettingsUpdateSuccessful)
        .settingResponseDTO(settingResponseDTOList)
        .build();
  }
}
