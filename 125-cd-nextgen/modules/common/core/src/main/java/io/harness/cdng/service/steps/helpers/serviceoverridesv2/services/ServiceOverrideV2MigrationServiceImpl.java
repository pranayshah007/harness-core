/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_SERVICE_OVERRIDE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.serviceoverridev2.beans.AccountLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.OrgLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ProjectLevelOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideMigrationResponseDTO.ServiceOverrideMigrationResponseDTOBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec.ServiceOverridesSpecBuilder;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.beans.SingleEnvMigrationResponse;
import io.harness.ng.core.serviceoverridev2.beans.SingleServiceOverrideMigrationResponse;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Slf4j
public class ServiceOverrideV2MigrationServiceImpl implements ServiceOverrideV2MigrationService {
  @Inject MongoTemplate mongoTemplate;
  @Inject NGSettingsClient settingsClient;
  private static final String DEBUG_LINE = "[ServiceOverrideV2MigrationServiceImpl]: ";

  @Override
  @NonNull
  public ServiceOverrideMigrationResponseDTO migrateToV2(
      @NonNull String accountId, String orgId, String projectId, boolean migrateChildren, boolean isRevert) {
    ServiceOverrideMigrationResponseDTOBuilder responseDTOBuilder =
        ServiceOverrideMigrationResponseDTO.builder().accountId(accountId);
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelResponseDTOs = new ArrayList<>();
    List<OrgLevelOverrideMigrationResponseDTO> orgLevelResponseDTOs = new ArrayList<>();
    if (isNotEmpty(projectId)) {
      log.info(String.format(
          DEBUG_LINE + "Starting project level migration for orgId: [%s], project :[%s]", orgId, projectId));
      projectLevelResponseDTOs = List.of(doProjectLevelMigration(accountId, orgId, projectId, isRevert));
      log.info(
          String.format(DEBUG_LINE + "Successfully finished project level migration for orgId: [%s], project :[%s]",
              orgId, projectId));
      ServiceOverrideMigrationResponseDTO migrationResponseDTO =
          responseDTOBuilder.projectLevelMigrationInfo(projectLevelResponseDTOs).build();
      migrationResponseDTO.setSuccessful(isOverallSuccessful(migrationResponseDTO));
      return migrationResponseDTO;
    }

    if (isNotEmpty(orgId)) {
      log.info(String.format(DEBUG_LINE + "Starting org level migration for orgId: [%s]", orgId));
      OrgLevelOverrideMigrationResponseDTO orgLevelResponseDTO = doOrgLevelMigration(accountId, orgId, isRevert);
      if (migrateChildren) {
        projectLevelResponseDTOs = doChildProjectsMigration(accountId, orgId, isRevert);
      }
      log.info(String.format(DEBUG_LINE + "Successfully finished org level migration for orgId: [%s]", orgId));

      ServiceOverrideMigrationResponseDTO migrationResponseDTO =
          responseDTOBuilder.orgLevelMigrationInfo(List.of(orgLevelResponseDTO))
              .projectLevelMigrationInfo(projectLevelResponseDTOs)
              .build();
      migrationResponseDTO.setSuccessful(isOverallSuccessful(migrationResponseDTO));

      return migrationResponseDTO;
    }

    log.info(String.format(DEBUG_LINE + "Starting account level migration for orgId: [%s]", accountId));

    AccountLevelOverrideMigrationResponseDTO accountLevelResponseDTO = doAccountLevelMigration(accountId, isRevert);
    if (migrateChildren) {
      orgLevelResponseDTOs = doChildLevelOrgMigration(accountId, isRevert);
      List<String> orgIdsInAccount = orgLevelResponseDTOs.stream()
                                         .map(OrgLevelOverrideMigrationResponseDTO::getOrgIdentifier)
                                         .collect(Collectors.toList());
      for (String localOrgId : orgIdsInAccount) {
        projectLevelResponseDTOs.addAll(doChildProjectsMigration(accountId, localOrgId, isRevert));
      }
    }
    log.info(String.format(DEBUG_LINE + "Successfully finished account level migration for account: [%s]", accountId));
    ServiceOverrideMigrationResponseDTO migrationResponseDTO =
        responseDTOBuilder.accountLevelMigrationInfo(accountLevelResponseDTO)
            .orgLevelMigrationInfo(orgLevelResponseDTOs)
            .projectLevelMigrationInfo(projectLevelResponseDTOs)
            .build();
    migrationResponseDTO.setSuccessful(isOverallSuccessful(migrationResponseDTO));
    return migrationResponseDTO;
  }

  @NonNull
  private ProjectLevelOverrideMigrationResponseDTO doProjectLevelMigration(
      String accountId, String orgId, String projectId, boolean isRevert) {
    boolean isOverrideMigrationSuccessFul = true;
    OverridesGroupMigrationResult overrideResult = OverridesGroupMigrationResult.builder().build();

    try {
      Criteria criteria = getCriteriaForProjectServiceOverrides(accountId, orgId, projectId, isRevert);
      overrideResult = doLevelScopedOverridesMigration(accountId, orgId, projectId, criteria, isRevert);
      isOverrideMigrationSuccessFul = overrideResult.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Override Migration failed for project with orgId: [%s], project :[%s]",
                    orgId, projectId),
          e);
      isOverrideMigrationSuccessFul = false;
    }

    boolean isEnvMigrationSuccessful = true;
    EnvsMigrationResult envResult = EnvsMigrationResult.builder().build();
    try {
      Criteria criteria = getCriteriaForProjectEnvironments(accountId, orgId, projectId, isRevert);
      envResult = doLevelScopedEnvMigration(accountId, orgId, projectId, criteria, isRevert);
      isEnvMigrationSuccessful = envResult.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(
                    DEBUG_LINE + "Env Migration failed for project with orgId: [%s], project :[%s]", orgId, projectId),
          e);
      isEnvMigrationSuccessful = false;
    }

    return ProjectLevelOverrideMigrationResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .isOverridesMigrationSuccessFul(isOverrideMigrationSuccessFul)
        .totalServiceOverridesCount(overrideResult.getTotalServiceOverrideCount())
        .migratedServiceOverridesCount(overrideResult.getMigratedServiceOverridesCount())
        .serviceOverridesInfos(overrideResult.getMigratedServiceOverridesInfos())
        .isEnvMigrationSuccessful(isEnvMigrationSuccessful)
        .totalEnvironmentsCount(envResult.getTargetEnvCount())
        .migratedEnvCount(envResult.getMigratedEnvCount())
        .migratedEnvironmentsInfo(envResult.getMigratedEnvInfos())
        .build();
  }

  private EnvsMigrationResult doLevelScopedEnvMigration(
      String accountId, String orgId, String projectId, Criteria criteria, boolean isRevert) {
    long migratedEnvCount = 0L;
    long totalEnvCount = 0L;
    boolean isSuccessFul = true;
    List<SingleEnvMigrationResponse> migratedEnvInfos = new ArrayList<>();

    try {
      Query queryForTargetedEnvs = new Query(criteria);
      totalEnvCount = mongoTemplate.count(queryForTargetedEnvs, Environment.class);

      if (totalEnvCount > 0L) {
        try (CloseableIterator<Environment> iterator = mongoTemplate.stream(queryForTargetedEnvs, Environment.class)) {
          while (iterator.hasNext()) {
            Environment envEntity = iterator.next();
            Optional<SingleEnvMigrationResponse> singleMigrationResponseOp =
                doMigrationForSingleEnvironment(envEntity, isRevert);
            if (singleMigrationResponseOp.isEmpty()) {
              migratedEnvCount++;
              migratedEnvInfos.add(SingleEnvMigrationResponse.builder()
                                       .accountId(envEntity.getAccountId())
                                       .orgId(envEntity.getOrgIdentifier())
                                       .projectId(envEntity.getProjectIdentifier())
                                       .envIdentifier(envEntity.getIdentifier())
                                       .isSuccessful(true)
                                       .build());
            } else {
              SingleEnvMigrationResponse singleMigrationResponse = singleMigrationResponseOp.get();
              migratedEnvInfos.add(singleMigrationResponse);
              if (!singleMigrationResponse.isSuccessful()) {
                isSuccessFul = false;
              } else {
                migratedEnvCount++;
              }
            }
          }
        }
        if (totalEnvCount != migratedEnvCount) {
          isSuccessFul = false;
        }
      }

    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE
                        + "Migration failed for env in scoped defined by projectId: [%s], orgId: [%s], accountId: [%s]",
                    projectId, orgId, accountId),
          e);
      isSuccessFul = false;
    }

    return EnvsMigrationResult.builder()
        .isSuccessFul(isSuccessFul)
        .targetEnvCount(totalEnvCount)
        .migratedEnvCount(migratedEnvCount)
        .migratedEnvInfos(migratedEnvInfos)
        .build();
  }

  private Criteria getCriteriaForProjectEnvironments(
      String accountId, String orgId, String projectId, boolean isRevert) {
    Criteria criteria = new Criteria()
                            .and(EnvironmentKeys.accountId)
                            .is(accountId)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(orgId)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectId);

    Criteria additionalCriteria;

    if (isRevert) {
      additionalCriteria = Criteria.where(EnvironmentKeys.isMigratedToOverride).is(true);
    } else {
      additionalCriteria = new Criteria().orOperator(Criteria.where(EnvironmentKeys.isMigratedToOverride).exists(false),
          Criteria.where(EnvironmentKeys.isMigratedToOverride).is(false));
    }

    return criteria.andOperator(additionalCriteria, new Criteria().and(EnvironmentKeys.yaml).exists(true).ne(null));
  }

  @NonNull
  private OrgLevelOverrideMigrationResponseDTO doOrgLevelMigration(String accountId, String orgId, boolean isRevert) {
    boolean isSuccessFul = true;
    OverridesGroupMigrationResult result = OverridesGroupMigrationResult.builder().build();

    try {
      Criteria criteria = getCriteriaForOrgServiceOverrides(accountId, orgId, isRevert);
      result = doLevelScopedOverridesMigration(accountId, orgId, null, criteria, isRevert);
      isSuccessFul = result.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Override Migration failed for project with orgId: [%s]", orgId), e);
      isSuccessFul = false;
    }

    boolean isEnvMigrationSuccessful = true;
    EnvsMigrationResult envResult = EnvsMigrationResult.builder().build();
    try {
      Criteria criteria = getCriteriaForOrgEnvs(accountId, orgId, isRevert);
      envResult = doLevelScopedEnvMigration(accountId, orgId, null, criteria, isRevert);
      isEnvMigrationSuccessful = envResult.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Env Migration failed for project with orgId: [%s]", orgId), e);
      isEnvMigrationSuccessful = false;
    }

    return OrgLevelOverrideMigrationResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .isOverridesMigrationSuccessFul(isSuccessFul)
        .totalServiceOverridesCount(result.getTotalServiceOverrideCount())
        .migratedServiceOverridesCount(result.getMigratedServiceOverridesCount())
        .serviceOverridesInfo(result.getMigratedServiceOverridesInfos())
        .isEnvsMigrationSuccessful(isEnvMigrationSuccessful)
        .totalEnvironmentsCount(envResult.getTargetEnvCount())
        .migratedEnvironmentCount(envResult.getMigratedEnvCount())
        .environmentsInfo(envResult.getMigratedEnvInfos())
        .build();
  }

  @NonNull
  private AccountLevelOverrideMigrationResponseDTO doAccountLevelMigration(String accountId, boolean isRevert) {
    boolean isSuccessFul = true;
    OverridesGroupMigrationResult result = OverridesGroupMigrationResult.builder().build();

    try {
      Criteria criteria = getCriteriaForAccountServiceOverrides(accountId, isRevert);
      result = doLevelScopedOverridesMigration(accountId, null, null, criteria, isRevert);
      isSuccessFul = result.isSuccessFul();
    } catch (Exception e) {
      isSuccessFul = false;
      log.error(String.format(DEBUG_LINE + "Override Migration failed for project with accountId: [%s]", accountId), e);
    }

    boolean isEnvMigrationSuccessful = true;
    EnvsMigrationResult envResult = EnvsMigrationResult.builder().build();
    try {
      Criteria criteria = getCriteriaForAccountEnvs(accountId, isRevert);
      envResult = doLevelScopedEnvMigration(accountId, null, null, criteria, isRevert);
      isEnvMigrationSuccessful = envResult.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Env Migration failed for project with accountId: [%s]", accountId), e);
      isEnvMigrationSuccessful = false;
    }

    return AccountLevelOverrideMigrationResponseDTO.builder()
        .accountId(accountId)
        .isOverridesMigrationSuccessFul(isSuccessFul)
        .totalServiceOverridesCount(result.getTotalServiceOverrideCount())
        .migratedServiceOverridesCount(result.getMigratedServiceOverridesCount())
        .serviceOverridesInfo(result.getMigratedServiceOverridesInfos())
        .isEnvsMigrationSuccessful(isEnvMigrationSuccessful)
        .targetEnvironmentsCount(envResult.getTargetEnvCount())
        .migratedEnvironmentsCount(envResult.getMigratedEnvCount())
        .environmentsInfo(envResult.getMigratedEnvInfos())
        .build();
  }

  @NonNull
  private List<ProjectLevelOverrideMigrationResponseDTO> doChildProjectsMigration(
      String accountId, String orgId, boolean isRevert) {
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelResponseDTOS = new ArrayList<>();

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
        ProjectLevelOverrideMigrationResponseDTO projectLevelResponseDTO =
            doProjectLevelMigration(accountId, orgId, projectId, isRevert);
        projectLevelResponseDTOS.add(projectLevelResponseDTO);
      }
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Migration failed for children projects of org: [%s]", orgId), e);
    }

    return projectLevelResponseDTOS;
  }

  @NonNull
  private List<OrgLevelOverrideMigrationResponseDTO> doChildLevelOrgMigration(String accountId, boolean isRevert) {
    List<OrgLevelOverrideMigrationResponseDTO> orgLevelResponseDTOS = new ArrayList<>();

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
        OrgLevelOverrideMigrationResponseDTO orgLevelResponseDTO = doOrgLevelMigration(accountId, orgId, isRevert);
        orgLevelResponseDTOS.add(orgLevelResponseDTO);
      }
    } catch (Exception e) {
      log.error(
          String.format(DEBUG_LINE + "Migration failed for children organizations of account: [%s]", accountId), e);
    }
    return orgLevelResponseDTOS;
  }

  @NonNull
  private SingleServiceOverrideMigrationResponse doMigrationForSingleOverrideEntity(
      NGServiceOverridesEntity overridesEntity, boolean isRevert) {
    try {
      Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.id).is(overridesEntity.getId());
      Query query = new org.springframework.data.mongodb.core.query.Query(criteria);
      Update update = new Update();
      if (isRevert) {
        update.set(NGServiceOverridesEntityKeys.isV2, Boolean.FALSE);
      } else {
        NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
            ServiceOverridesMapper.toNGServiceOverrideConfig(overridesEntity.getYaml()).getServiceOverrideInfoConfig();
        ServiceOverridesSpec spec = ServiceOverridesSpec.builder()
                                        .variables(serviceOverrideInfoConfig.getVariables())
                                        .manifests(serviceOverrideInfoConfig.getManifests())
                                        .configFiles(serviceOverrideInfoConfig.getConfigFiles())
                                        .applicationSettings(serviceOverrideInfoConfig.getApplicationSettings())
                                        .connectionStrings(serviceOverrideInfoConfig.getConnectionStrings())
                                        .build();

        update.set(NGServiceOverridesEntityKeys.spec, spec);
        update.set(NGServiceOverridesEntityKeys.isV2, Boolean.TRUE);
      }
      mongoTemplate.updateFirst(query, update, NGServiceOverridesEntity.class);
      return SingleServiceOverrideMigrationResponse.builder()
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
              "Service Override migration failed for override with serviceRef: [%s], environmentRef: [%s], projectId: [%s], orgId: [%s]",
              overridesEntity.getServiceRef(), overridesEntity.getEnvironmentRef(),
              overridesEntity.getProjectIdentifier(), overridesEntity.getOrgIdentifier()),
          e);

      return SingleServiceOverrideMigrationResponse.builder()
          .isSuccessful(false)
          .serviceRef(overridesEntity.getServiceRef())
          .envRef(overridesEntity.getEnvironmentRef())
          .projectId(overridesEntity.getProjectIdentifier())
          .orgId(overridesEntity.getOrgIdentifier())
          .accountId(overridesEntity.getAccountId())
          .build();
    }
  }

  @NonNull
  private Criteria getCriteriaForProjectServiceOverrides(
      String accountId, String orgId, String projectId, boolean isRevert) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(NGServiceOverridesEntityKeys.projectIdentifier)
                            .is(projectId);

    Criteria additionalCriteria;
    if (isRevert) {
      additionalCriteria =
          new Criteria().andOperator(Criteria.where(NGServiceOverridesEntityKeys.isV2).is(Boolean.TRUE),
              Criteria.where(NGServiceOverridesEntityKeys.type).is(ENV_SERVICE_OVERRIDE));
    } else {
      additionalCriteria = new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.isV2).exists(false),
          Criteria.where(NGServiceOverridesEntityKeys.isV2).is(Boolean.FALSE));
    }

    return criteria.andOperator(additionalCriteria);
  }

  @NonNull
  private Criteria getCriteriaForOrgServiceOverrides(String accountId, String orgId, boolean isRevert) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId);

    Criteria additionalCriteria;
    if (isRevert) {
      additionalCriteria =
          new Criteria().andOperator(Criteria.where(NGServiceOverridesEntityKeys.isV2).is(Boolean.TRUE),
              Criteria.where(NGServiceOverridesEntityKeys.type).is(ENV_SERVICE_OVERRIDE));
    } else {
      additionalCriteria = new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.isV2).exists(false),
          Criteria.where(NGServiceOverridesEntityKeys.isV2).is(Boolean.FALSE));
    }

    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).is(null)),
        additionalCriteria);
  }

  @NonNull
  private Criteria getCriteriaForAccountServiceOverrides(String accountId, boolean isRevert) {
    Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.accountId).is(accountId);

    Criteria additionalCriteria;
    if (isRevert) {
      additionalCriteria =
          new Criteria().andOperator(Criteria.where(NGServiceOverridesEntityKeys.isV2).is(Boolean.TRUE),
              Criteria.where(NGServiceOverridesEntityKeys.type).is(ENV_SERVICE_OVERRIDE));
    } else {
      additionalCriteria = new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.isV2).exists(false),
          Criteria.where(NGServiceOverridesEntityKeys.isV2).is(Boolean.FALSE));
    }

    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).is(null)),
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).is(null)),
        additionalCriteria);
  }

  @NonNull
  private Criteria getCriteriaForOrgEnvs(String accountId, String orgId, boolean isRevert) {
    Criteria criteria =
        new Criteria().and(EnvironmentKeys.accountId).is(accountId).and(EnvironmentKeys.orgIdentifier).is(orgId);
    Criteria additionalCriteria;

    if (isRevert) {
      additionalCriteria = Criteria.where(EnvironmentKeys.isMigratedToOverride).is(true);
    } else {
      additionalCriteria = new Criteria().orOperator(Criteria.where(EnvironmentKeys.isMigratedToOverride).exists(false),
          Criteria.where(EnvironmentKeys.isMigratedToOverride).is(false));
    }

    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(EnvironmentKeys.projectIdentifier).exists(false),
            Criteria.where(EnvironmentKeys.projectIdentifier).is(null)),
        additionalCriteria, new Criteria().and(EnvironmentKeys.yaml).exists(true).ne(null));
  }

  @NonNull
  private Criteria getCriteriaForAccountEnvs(String accountId, boolean isRevert) {
    Criteria criteria = new Criteria().and(EnvironmentKeys.accountId).is(accountId);
    Criteria additionalCriteria;
    if (isRevert) {
      additionalCriteria = Criteria.where(EnvironmentKeys.isMigratedToOverride).is(true);
    } else {
      additionalCriteria = new Criteria().orOperator(Criteria.where(EnvironmentKeys.isMigratedToOverride).exists(false),
          Criteria.where(EnvironmentKeys.isMigratedToOverride).is(false));
    }

    return criteria.andOperator(new Criteria().orOperator(Criteria.where(EnvironmentKeys.orgIdentifier).exists(false),
                                    Criteria.where(EnvironmentKeys.orgIdentifier).is(null)),
        new Criteria().orOperator(Criteria.where(EnvironmentKeys.projectIdentifier).exists(false),
            Criteria.where(EnvironmentKeys.projectIdentifier).is(null)),
        additionalCriteria, new Criteria().and(EnvironmentKeys.yaml).exists(true).ne(null));
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class OverridesGroupMigrationResult {
    long migratedServiceOverridesCount;
    long totalServiceOverrideCount;
    long targetServiceOverridesCount;
    boolean isSuccessFul;
    List<SingleServiceOverrideMigrationResponse> migratedServiceOverridesInfos;
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class EnvsMigrationResult {
    long migratedEnvCount;
    long targetEnvCount;
    boolean isSuccessFul;
    List<SingleEnvMigrationResponse> migratedEnvInfos;
  }
  private OverridesGroupMigrationResult doLevelScopedOverridesMigration(
      String accountId, String orgId, String projectId, Criteria criteria, boolean isRevert) {
    long migratedServiceOverridesCount = 0L;
    long totalServiceOverride = 0L;
    boolean isSuccessFul = true;
    List<SingleServiceOverrideMigrationResponse> migratedServiceOverridesInfos = new ArrayList<>();

    try {
      Query queryForEntitiesToBeUpdated = new Query(criteria);
      totalServiceOverride = mongoTemplate.count(queryForEntitiesToBeUpdated, NGServiceOverridesEntity.class);
      if (totalServiceOverride > 0L) {
        try (CloseableIterator<NGServiceOverridesEntity> iterator =
                 mongoTemplate.stream(queryForEntitiesToBeUpdated, NGServiceOverridesEntity.class)) {
          while (iterator.hasNext()) {
            NGServiceOverridesEntity overridesEntity = iterator.next();
            SingleServiceOverrideMigrationResponse singleMigrationResponse =
                doMigrationForSingleOverrideEntity(overridesEntity, isRevert);
            migratedServiceOverridesInfos.add(singleMigrationResponse);
            if (!singleMigrationResponse.isSuccessful()) {
              isSuccessFul = false;
            } else {
              migratedServiceOverridesCount++;
            }
          }
        }
        if (totalServiceOverride != migratedServiceOverridesCount) {
          isSuccessFul = false;
          log.error(String.format(DEBUG_LINE
                  + "Migrated count [%d] and Target count [%d] does not match, projectId: [%s], orgId: [%s], accountId: [%s]",
              migratedServiceOverridesCount, totalServiceOverride, projectId, orgId, accountId));
        }
      }

    } catch (Exception e) {
      log.error(String.format(
                    DEBUG_LINE + "Migration failed for scoped defined by projectId: [%s], orgId: [%s], accountId: [%s]",
                    projectId, orgId, accountId),
          e);
      isSuccessFul = false;
    }

    return OverridesGroupMigrationResult.builder()
        .isSuccessFul(isSuccessFul)
        .totalServiceOverrideCount(totalServiceOverride)
        .migratedServiceOverridesCount(migratedServiceOverridesCount)
        .migratedServiceOverridesInfos(migratedServiceOverridesInfos)
        .build();
  }

  @NonNull
  private Optional<SingleEnvMigrationResponse> doMigrationForSingleEnvironment(
      Environment envEntity, boolean isRevert) {
    try {
      NGEnvironmentInfoConfig envNGConfig =
          EnvironmentMapper.toNGEnvironmentConfig(envEntity.getYaml()).getNgEnvironmentInfoConfig();
      if (isEmpty(envNGConfig.getVariables()) && isNoOverridesPresent(envNGConfig.getNgEnvironmentGlobalOverride())) {
        return Optional.empty();
      }

      NGServiceOverridesEntity overridesEntity = convertEnvToOverrideEntity(envEntity, envNGConfig);
      if (isRevert) {
        Criteria criteria = new Criteria()
                                .and(NGServiceOverridesEntityKeys.type)
                                .is(ENV_GLOBAL_OVERRIDE)
                                .and(NGServiceOverridesEntityKeys.identifier)
                                .is(overridesEntity.getIdentifier())
                                .and(NGServiceOverridesEntityKeys.accountId)
                                .is(overridesEntity.getAccountId());
        Query query = new Query(criteria);
        mongoTemplate.remove(query, NGServiceOverridesEntity.class);
      } else {
        mongoTemplate.save(overridesEntity);
      }
      boolean isEnvUpdateSuccessful = updateEnvironmentForMigration(envEntity, isRevert);

      return Optional.of(SingleEnvMigrationResponse.builder()
                             .isSuccessful(isEnvUpdateSuccessful)
                             .envIdentifier(overridesEntity.getEnvironmentRef())
                             .projectId(overridesEntity.getProjectIdentifier())
                             .orgId(overridesEntity.getOrgIdentifier())
                             .accountId(overridesEntity.getAccountId())
                             .build());
    } catch (Exception e) {
      log.error(
          String.format(DEBUG_LINE
                  + "Env to ServiceOverride migration failed for envId: [%s], projectId: [%s], orgId: [%s], accountId: [%s]",
              envEntity.getIdentifier(), envEntity.getProjectIdentifier(), envEntity.getOrgIdentifier(),
              envEntity.getAccountId()),
          e);
    }
    return Optional.of(SingleEnvMigrationResponse.builder()
                           .isSuccessful(false)
                           .envIdentifier(envEntity.getIdentifier())
                           .projectId(envEntity.getProjectIdentifier())
                           .orgId(envEntity.getOrgIdentifier())
                           .accountId(envEntity.getAccountId())
                           .build());
  }

  private NGServiceOverridesEntity convertEnvToOverrideEntity(
      Environment envEntity, NGEnvironmentInfoConfig envNGConfig) {
    NGEnvironmentGlobalOverride ngEnvOverride = envNGConfig.getNgEnvironmentGlobalOverride();

    ServiceOverridesSpecBuilder specBuilder = ServiceOverridesSpec.builder().variables(envNGConfig.getVariables());
    if (ngEnvOverride != null) {
      specBuilder.manifests(ngEnvOverride.getManifests())
          .configFiles(ngEnvOverride.getConfigFiles())
          .applicationSettings(ngEnvOverride.getApplicationSettings())
          .connectionStrings(ngEnvOverride.getConnectionStrings());
    }

    String scopedEnvRef = IdentifierRefHelper.getRefFromIdentifierOrRef(envEntity.getAccountId(),
        envEntity.getOrgIdentifier(), envEntity.getProjectIdentifier(), envEntity.getIdentifier());
    IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(
        scopedEnvRef, envEntity.getAccountId(), envEntity.getOrgIdentifier(), envEntity.getProjectIdentifier());

    return NGServiceOverridesEntity.builder()
        .identifier(generateEnvOverrideIdentifier(scopedEnvRef))
        .environmentRef(scopedEnvRef)
        .projectIdentifier(envIdentifierRef.getProjectIdentifier())
        .orgIdentifier(envIdentifierRef.getOrgIdentifier())
        .accountId(envIdentifierRef.getAccountIdentifier())
        .type(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
        .spec(specBuilder.build())
        .isV2(Boolean.TRUE)
        .build();
  }

  private boolean updateEnvironmentForMigration(Environment envEntity, boolean isRevert) {
    try {
      Criteria criteria = new Criteria().and(EnvironmentKeys.id).is(envEntity.getId());
      Query query = new Query(criteria);
      Update update = new Update();
      update.set(EnvironmentKeys.isMigratedToOverride, !isRevert);
      mongoTemplate.updateFirst(query, update, Environment.class);
      return true;
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE
                        + "Environment update failed for envId: [%s], projectId: [%s], orgId: [%s], accountId: [%s]",
                    envEntity.getIdentifier(), envEntity.getProjectIdentifier(), envEntity.getOrgIdentifier(),
                    envEntity.getAccountId()),
          e);
    }
    return false;
  }

  private boolean isNoOverridesPresent(NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride) {
    if (ngEnvironmentGlobalOverride == null) {
      return true;
    }
    return isEmpty(ngEnvironmentGlobalOverride.getManifests()) && isEmpty(ngEnvironmentGlobalOverride.getConfigFiles())
        && ngEnvironmentGlobalOverride.getConnectionStrings() == null
        && ngEnvironmentGlobalOverride.getApplicationSettings() == null;
  }

  private String generateEnvOverrideIdentifier(String envRef) {
    return String.join("_", envRef).replace(".", "_");
  }

  private boolean isOverallSuccessful(ServiceOverrideMigrationResponseDTO responseDTO) {
    boolean isSuccessful = true;

    if (isNotEmpty(responseDTO.getProjectLevelMigrationInfo())) {
      isSuccessful = checkSuccessInProjects(responseDTO);
    }

    if (isNotEmpty(responseDTO.getOrgLevelMigrationInfo())) {
      isSuccessful = isSuccessful && checkSuccessInOrgs(responseDTO);
    }

    if (responseDTO.getAccountLevelMigrationInfo() != null) {
      isSuccessful = isSuccessful && checkSuccessInAccount(responseDTO);
    }

    return isSuccessful;
  }

  private boolean checkSuccessInAccount(ServiceOverrideMigrationResponseDTO responseDTO) {
    return responseDTO.getAccountLevelMigrationInfo().isOverridesMigrationSuccessFul()
        && responseDTO.getAccountLevelMigrationInfo().isEnvsMigrationSuccessful();
  }

  private boolean checkSuccessInOrgs(ServiceOverrideMigrationResponseDTO responseDTO) {
    return !(
        responseDTO.getOrgLevelMigrationInfo().stream().anyMatch(orgDto -> !orgDto.isOverridesMigrationSuccessFul())
        || responseDTO.getOrgLevelMigrationInfo().stream().anyMatch(orgDto -> !orgDto.isEnvsMigrationSuccessful()));
  }

  private boolean checkSuccessInProjects(ServiceOverrideMigrationResponseDTO responseDTO) {
    return !(responseDTO.getProjectLevelMigrationInfo().stream().anyMatch(
                 projectDto -> !projectDto.isOverridesMigrationSuccessFul())
        || responseDTO.getProjectLevelMigrationInfo().stream().anyMatch(
            projectDto -> !projectDto.isEnvMigrationSuccessful()));
  }
}
