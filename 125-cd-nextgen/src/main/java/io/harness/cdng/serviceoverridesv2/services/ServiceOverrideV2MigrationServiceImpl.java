/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideMigrationResponseDTO.AccountLevelOverrideMigrationResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideMigrationResponseDTO.OrgLevelOverrideMigrationResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideMigrationResponseDTO.ProjectLevelOverrideMigrationResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO.ServiceOverrideMigrationResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.SingleServiceOverrideMigrationResponse;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;

import com.google.inject.Inject;
import java.net.CacheRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

public class ServiceOverrideV2MigrationServiceImpl implements ServiceOverrideV2MigrationService {
  @Inject ServiceOverrideRepository serviceOverrideRepository;
  @Inject MongoTemplate mongoTemplate;
  @Override
  @NonNull
  public ServiceOverrideMigrationResponseDTO migrateToV2(@NonNull String accountId, String orgId, String projectId) {
    ServiceOverrideMigrationResponseDTOBuilder responseDTOBuilder =
        ServiceOverrideMigrationResponseDTO.builder().accountId(accountId);
    if (isNotEmpty(projectId)) {
      ProjectLevelOverrideMigrationResponseDTO projectLevelResponseDTOs =
          doProjectMigration(accountId, orgId, projectId);
      return responseDTOBuilder.projectLevelMigrationInfo(List.of(projectLevelResponseDTOs)).build();
    }

    if (isNotEmpty(orgId)) {
      OrgLevelOverrideMigrationResponseDTO orgLevelResponseDTO = doOrgLevelMigration(accountId, orgId);
      List<ProjectLevelOverrideMigrationResponseDTO> projectLevelResponseDTOs =
          doChildProjectsMigration(accountId, orgId);
      return responseDTOBuilder.orgLevelMigrationInfo(List.of(orgLevelResponseDTO))
          .projectLevelMigrationInfo(projectLevelResponseDTOs)
          .build();
    }

    AccountLevelOverrideMigrationResponseDTO accountLevelResponseDTO = doAccountLevelMigration(accountId);
    List<OrgLevelOverrideMigrationResponseDTO> orgLevelResponseDTO = doChildLevelOrgMigration(accountId);
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelResponseDTOs = new ArrayList<>();
    List<String> orgIdsInAccount = orgLevelResponseDTO.stream()
                                       .map(OrgLevelOverrideMigrationResponseDTO::getOrgIdentifier)
                                       .collect(Collectors.toList());
    for (String localOrgId : orgIdsInAccount) {
      projectLevelResponseDTOs.addAll(doChildProjectsMigration(accountId, localOrgId));
    }
    return responseDTOBuilder.accountLevelMigrationInfo(accountLevelResponseDTO)
        .orgLevelMigrationInfo(orgLevelResponseDTO)
        .projectLevelMigrationInfo(projectLevelResponseDTOs)
        .build();
  }

  @NonNull
  private List<OrgLevelOverrideMigrationResponseDTO> doChildLevelOrgMigration(String accountId) {
    return null;
  }

  @NonNull
  private AccountLevelOverrideMigrationResponseDTO doAccountLevelMigration(String accountId) {
    return null;
  }

  @NonNull
  private List<ProjectLevelOverrideMigrationResponseDTO> doChildProjectsMigration(String accountId, String orgId) {
    return null;
  }

  @NonNull
  private OrgLevelOverrideMigrationResponseDTO doOrgLevelMigration(String accountId, String orgId) {
    return null;
  }

  @NonNull
  private ProjectLevelOverrideMigrationResponseDTO doProjectMigration(
      String accountId, String orgId, String projectId) {
    ProjectLevelOverrideMigrationResponseDTOBuilder projectLevelResponseDTOBuilder =
        ProjectLevelOverrideMigrationResponseDTO.builder().accountId(accountId).orgIdentifier(orgId).projectIdentifier(
            projectId);

    int migratedEnvironmentsCount = 0;
    int migratedServiceOverridesCount = 0;

    int totalServiceOverride = -1;

    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(NGServiceOverridesEntityKeys.projectIdentifier)
                            .is(projectId);

    List<SingleServiceOverrideMigrationResponse> migratedServiceOverridesInfos = new ArrayList<>();
    List<String> migratedEnvironmentsInfos = new ArrayList<>();

    List<NGServiceOverridesEntity> overridesInProject = serviceOverrideRepository.findAll(criteria);
    for (NGServiceOverridesEntity overridesEntity : overridesInProject) {
      SingleServiceOverrideMigrationResponse overrideMigrationResponse = doMigrationForSingleEntity(overridesEntity);
    }

    return null;
  }

  @NonNull
  private SingleServiceOverrideMigrationResponse doMigrationForSingleEntity(NGServiceOverridesEntity overridesEntity) {
    Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.id).is(overridesEntity.getId());
    Update update = new Update();
    update.set()

        return null
  }
}
