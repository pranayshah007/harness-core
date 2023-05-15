/*
 * Copyright 20L23 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0L.0L license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/20L20L/0L5/PolyForm-Free-Trial-1.0L.0L.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

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
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.beans.SingleEnvMigrationResponse;
import io.harness.ng.core.serviceoverridev2.beans.SingleServiceOverrideMigrationResponse;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
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
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@Slf4j
public class ServiceOverrideV2MigrationServiceImpl implements ServiceOverrideV2MigrationService {
  @Inject MongoTemplate mongoTemplate;
  @Inject ServiceOverridesRepositoryV2 serviceOverridesRepositoryV2;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final static String DEBUG_LINE = "[ServiceOverrideV2MigrationServiceImpl]: ";
  @Override
  @NonNull
  public ServiceOverrideMigrationResponseDTO migrateToV2(@NonNull String accountId, String orgId, String projectId) {
    ServiceOverrideMigrationResponseDTOBuilder responseDTOBuilder =
        ServiceOverrideMigrationResponseDTO.builder().accountId(accountId);
    if (isNotEmpty(projectId)) {
      log.info(String.format(
          DEBUG_LINE + "Starting project level migration for orgId: [%s], project :[%s]", orgId, projectId));
      ProjectLevelOverrideMigrationResponseDTO projectLevelResponseDTOs =
          doProjectLevelMigration(accountId, orgId, projectId);
      log.info(
          String.format(DEBUG_LINE + "Successfully finished project level migration for orgId: [%s], project :[%s]",
              orgId, projectId));
      return responseDTOBuilder.projectLevelMigrationInfo(List.of(projectLevelResponseDTOs)).build();
    }

    if (isNotEmpty(orgId)) {
      log.info(String.format(DEBUG_LINE + "Starting org level migration for orgId: [%s]", orgId));
      OrgLevelOverrideMigrationResponseDTO orgLevelResponseDTO = doOrgLevelMigration(accountId, orgId);
      List<ProjectLevelOverrideMigrationResponseDTO> projectLevelResponseDTOs =
          doChildProjectsMigration(accountId, orgId);
      log.info(String.format(DEBUG_LINE + "Successfully finished org level migration for orgId: [%s]", orgId));
      return responseDTOBuilder.orgLevelMigrationInfo(List.of(orgLevelResponseDTO))
          .projectLevelMigrationInfo(projectLevelResponseDTOs)
          .build();
    }

    log.info(String.format(DEBUG_LINE + "Starting account level migration for orgId: [%s]", accountId));

    AccountLevelOverrideMigrationResponseDTO accountLevelResponseDTO = doAccountLevelMigration(accountId);
    List<OrgLevelOverrideMigrationResponseDTO> orgLevelResponseDTO = doChildLevelOrgMigration(accountId);
    List<ProjectLevelOverrideMigrationResponseDTO> projectLevelResponseDTOs = new ArrayList<>();
    List<String> orgIdsInAccount = orgLevelResponseDTO.stream()
                                       .map(OrgLevelOverrideMigrationResponseDTO::getOrgIdentifier)
                                       .collect(Collectors.toList());
    for (String localOrgId : orgIdsInAccount) {
      projectLevelResponseDTOs.addAll(doChildProjectsMigration(accountId, localOrgId));
    }
    log.info(String.format(DEBUG_LINE + "Successfully finished account level migration for account: [%s]", accountId));
    return responseDTOBuilder.accountLevelMigrationInfo(accountLevelResponseDTO)
        .orgLevelMigrationInfo(orgLevelResponseDTO)
        .projectLevelMigrationInfo(projectLevelResponseDTOs)
        .build();
  }

  @NonNull
  private ProjectLevelOverrideMigrationResponseDTO doProjectLevelMigration(
      String accountId, String orgId, String projectId) {
    boolean isSuccessFul = true;
    OverridesGroupMigrationResult overrideResult = OverridesGroupMigrationResult.builder().build();

    try {
      Criteria criteria = getCriteriaForProjectServiceOverrides(accountId, orgId, projectId);
      overrideResult = doLevelScopedOverridesMigration(accountId, orgId, projectId, criteria);
      isSuccessFul = overrideResult.isSuccessFul();
    } catch (Exception e) {
      log.error(
          String.format(DEBUG_LINE + "Migration failed for project with orgId: [%s], project :[%s]", orgId, projectId),
          e);
      isSuccessFul = false;
    }

    EnvsMigrationResult envResult = EnvsMigrationResult.builder().build();
    try {
      Criteria criteria = getCriteriaForProjectEnvironments(accountId, orgId, projectId);
      envResult= doLevelScopedEnvMigration(accountId, orgId, projectId, criteria);
    } catch (Exception e) {
      log.error(
              String.format(DEBUG_LINE + "Migration failed for project with orgId: [%s], project :[%s]", orgId, projectId),
              e);
      isSuccessFul = false;
    }
 // Todo : start from here
    /*
    set env count+ info
    create new env successful
     */
    return ProjectLevelOverrideMigrationResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .isSuccessFul(isSuccessFul)
        .totalServiceOverridesCount(overrideResult.getTotalServiceOverrideCount())
        .migratedServiceOverridesCount(overrideResult.getMigratedServiceOverridesCount())
        .serviceOverridesInfos(overrideResult.getMigratedServiceOverridesInfos())
        .build();
  }

  private EnvsMigrationResult doLevelScopedEnvMigration(
      String accountId, String orgId, String projectId, Criteria criteria) {
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
            Optional<SingleEnvMigrationResponse> singleMigrationResponseOp = doMigrationForSingleEnvironment(envEntity);
            if (singleMigrationResponseOp.isEmpty()) {
              migratedEnvCount++;
            } else {
              SingleEnvMigrationResponse singleMigrationResponse = singleMigrationResponseOp.get();
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

  private Criteria getCriteriaForProjectEnvironments(String accountId, String orgId, String projectId) {
    Criteria criteria = new Criteria()
                            .and(EnvironmentKeys.accountId)
                            .is(accountId)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(orgId)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectId)
                            .and(EnvironmentKeys.isMigratedToOverride)
                            .is(false);

    return criteria.andOperator(new Criteria().andOperator(
        Criteria.where(EnvironmentKeys.yaml).exists(true), Criteria.where(EnvironmentKeys.yaml).isNull().not()));
  }

  @NonNull
  private OrgLevelOverrideMigrationResponseDTO doOrgLevelMigration(String accountId, String orgId) {
    boolean isSuccessFul = true;
    OverridesGroupMigrationResult result = OverridesGroupMigrationResult.builder().build();

    try {
      Criteria criteria = getCriteriaForOrgServiceOverrides(accountId, orgId);
      result = doLevelScopedOverridesMigration(accountId, orgId, null, criteria);
      isSuccessFul = result.isSuccessFul();
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Migration failed for project with orgId: [%s]", orgId), e);
      isSuccessFul = false;
    }

    return OrgLevelOverrideMigrationResponseDTO.builder()
        .accountId(accountId)
        .orgIdentifier(orgId)
        .isSuccessFul(isSuccessFul)
        .totalServiceOverridesCount(result.getTotalServiceOverrideCount())
        .migratedServiceOverridesCount(result.getMigratedServiceOverridesCount())
        .serviceOverridesInfo(result.getMigratedServiceOverridesInfos())
        .build();
  }

  @NonNull
  private AccountLevelOverrideMigrationResponseDTO doAccountLevelMigration(String accountId) {
    boolean isSuccessFul = true;
    OverridesGroupMigrationResult result = OverridesGroupMigrationResult.builder().build();

    try {
      Criteria criteria = getCriteriaForAccountServiceOverrides(accountId);
      result = doLevelScopedOverridesMigration(accountId, null, null, criteria);
      isSuccessFul = result.isSuccessFul();
    } catch (Exception e) {
      isSuccessFul = false;
      log.error(String.format(DEBUG_LINE + "Migration failed for project with accountId: [%s]", accountId), e);
    }

    return AccountLevelOverrideMigrationResponseDTO.builder()
        .accountId(accountId)
        .isSuccessFul(isSuccessFul)
        .totalServiceOverridesCount(result.getTotalServiceOverrideCount())
        .migratedServiceOverridesCount(result.getMigratedServiceOverridesCount())
        .serviceOverridesInfo(result.getMigratedServiceOverridesInfos())
        .build();
  }

  @NonNull
  private List<ProjectLevelOverrideMigrationResponseDTO> doChildProjectsMigration(String accountId, String orgId) {
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
            doProjectLevelMigration(accountId, orgId, projectId);
        projectLevelResponseDTOS.add(projectLevelResponseDTO);
      }
    } catch (Exception e) {
      log.error(String.format(DEBUG_LINE + "Migration failed for children projects of org: [%s]", orgId), e);
    }

    return projectLevelResponseDTOS;
  }

  @NonNull
  private List<OrgLevelOverrideMigrationResponseDTO> doChildLevelOrgMigration(String accountId) {
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
        OrgLevelOverrideMigrationResponseDTO orgLevelResponseDTO = doOrgLevelMigration(accountId, orgId);
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
      NGServiceOverridesEntity overridesEntity) {
    try {
      NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          ServiceOverridesMapper.toNGServiceOverrideConfig(overridesEntity.getYaml()).getServiceOverrideInfoConfig();
      ServiceOverridesSpec spec = ServiceOverridesSpec.builder()
                                      .variables(serviceOverrideInfoConfig.getVariables())
                                      .manifests(serviceOverrideInfoConfig.getManifests())
                                      .configFiles(serviceOverrideInfoConfig.getConfigFiles())
                                      .applicationSettings(serviceOverrideInfoConfig.getApplicationSettings())
                                      .connectionStrings(serviceOverrideInfoConfig.getConnectionStrings())
                                      .build();

      Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.id).is(overridesEntity.getId());
      Query query = new org.springframework.data.mongodb.core.query.Query(criteria);
      Update update = new Update();
      update.set(NGServiceOverridesEntityKeys.spec, spec);
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

  @NotNull
  private static Criteria getCriteriaForProjectServiceOverrides(String accountId, String orgId, String projectId) {
    return new Criteria()
        .and(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(orgId)
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(projectId);
  }

  @NotNull
  private static Criteria getCriteriaForOrgServiceOverrides(String accountId, String orgId) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId);

    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).is(null)));
  }

  @NotNull
  private static Criteria getCriteriaForAccountServiceOverrides(String accountId) {
    Criteria criteria = new Criteria().and(NGServiceOverridesEntityKeys.accountId).is(accountId);
    criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.orgIdentifier).is(null)));
    return criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.projectIdentifier).is(null)));
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private class OverridesGroupMigrationResult {
    long migratedServiceOverridesCount;
    long totalServiceOverrideCount;
    long targetServiceOverridesCount;
    boolean isSuccessFul;
    List<SingleServiceOverrideMigrationResponse> migratedServiceOverridesInfos;
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private class EnvsMigrationResult {
    long migratedEnvCount;
    long targetEnvCount;
    boolean isSuccessFul;
    List<SingleEnvMigrationResponse> migratedEnvInfos;
  }
  private OverridesGroupMigrationResult doLevelScopedOverridesMigration(
      String accountId, String orgId, String projectId, Criteria criteria) {
    long migratedServiceOverridesCount = 0L;
    long totalServiceOverride = 0L;
    long targetServiceOverrides = 0L;
    boolean isSuccessFul = true;
    List<SingleServiceOverrideMigrationResponse> migratedServiceOverridesInfos = new ArrayList<>();

    try {
      Query queryForAllOverrides = new Query(criteria);
      totalServiceOverride = mongoTemplate.count(queryForAllOverrides, NGServiceOverridesEntity.class);

      if (totalServiceOverride > 0L) {
        criteria.andOperator(new Criteria().orOperator(Criteria.where(NGServiceOverridesEntityKeys.spec).exists(false),
            Criteria.where(NGServiceOverridesEntityKeys.spec).isNull()));
        Query queryForEntitiesToBeUpdated = new Query(criteria);
        targetServiceOverrides = mongoTemplate.count(queryForEntitiesToBeUpdated, NGServiceOverridesEntity.class);
        if (targetServiceOverrides > 0L) {
          try (CloseableIterator<NGServiceOverridesEntity> iterator =
                   mongoTemplate.stream(queryForEntitiesToBeUpdated, NGServiceOverridesEntity.class)) {
            while (iterator.hasNext()) {
              NGServiceOverridesEntity overridesEntity = iterator.next();
              SingleServiceOverrideMigrationResponse singleMigrationResponse =
                  doMigrationForSingleOverrideEntity(overridesEntity);
              migratedServiceOverridesInfos.add(singleMigrationResponse);
              if (!singleMigrationResponse.isSuccessful()) {
                isSuccessFul = false;
              } else {
                migratedServiceOverridesCount++;
              }
            }
          }
          if (targetServiceOverrides != migratedServiceOverridesCount) {
            isSuccessFul = false;
          }
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
  private Optional<SingleEnvMigrationResponse> doMigrationForSingleEnvironment(Environment envEntity) {
    try {
      NGEnvironmentInfoConfig envNGConfig =
          EnvironmentMapper.toNGEnvironmentConfig(envEntity.getYaml()).getNgEnvironmentInfoConfig();
      if (isEmpty(envNGConfig.getVariables()) && isNoOverridesPresent(envNGConfig.getNgEnvironmentGlobalOverride())) {
        return Optional.empty();
      }

      NGServiceOverridesEntity overridesEntity = convertEnvToOverrideEntity(envEntity, envNGConfig);
      serviceOverridesRepositoryV2.save(overridesEntity);
      boolean isEnvUpdateSuccessful = updateEnvironmentForMigration(envEntity);

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

    ServiceOverridesSpec spec = ServiceOverridesSpec.builder()
                                    .variables(envNGConfig.getVariables())
                                    .manifests(ngEnvOverride.getManifests())
                                    .configFiles(ngEnvOverride.getConfigFiles())
                                    .applicationSettings(ngEnvOverride.getApplicationSettings())
                                    .connectionStrings(ngEnvOverride.getConnectionStrings())
                                    .build();

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
        .spec(spec)
        .isV2(true)
        .build();
  }

  private boolean updateEnvironmentForMigration(Environment envEntity) {
    try {
      Criteria criteria = new Criteria().and(EnvironmentKeys.id).is(envEntity.getId());
      Query query = new Query(criteria);
      Update update = new Update();
      update.set(EnvironmentKeys.isMigratedToOverride, true);
      mongoTemplate.updateFirst(query, update, NGServiceOverridesEntity.class);
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

  public String generateEnvOverrideIdentifier(String envRef) {
    return String.join("_", envRef).replace(".", "_");
  }
}
