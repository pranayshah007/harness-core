/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness;

import static io.harness.licensing.Edition.COMMUNITY;
import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.licensing.Edition.FREE;
import static io.harness.licensing.Edition.TEAM;

import io.harness.beans.FeatureName;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.exceptions.EnforcementServiceConnectionException;
import io.harness.enforcement.exceptions.WrongFeatureStateException;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PipelineSettingsServiceImpl implements PipelineSettingsService {
  @Inject PlanExecutionService planExecutionService;
  @Inject EnforcementClientService enforcementClientService;

  @Inject NgLicenseHttpClient ngLicenseHttpClient;
  @Inject NGSettingsClient ngSettingsClient;
  @Inject OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  private final LoadingCache<String, List<ModuleLicenseDTO>> moduleLicensesCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(30, TimeUnit.MINUTES)
          .build(new CacheLoader<String, List<ModuleLicenseDTO>>() {
            @Override
            public List<ModuleLicenseDTO> load(@NotNull final String accountId) {
              return listAllEnabledFeatureFlagsForAccount(accountId);
            }
          });

  private List<ModuleLicenseDTO> listAllEnabledFeatureFlagsForAccount(String accountId) {
    return NGRestUtils.getResponse(ngLicenseHttpClient.getModuleLicenses(accountId));
  }

  private Edition getEdition(String accountId) throws ExecutionException {
    List<ModuleLicenseDTO> moduleLicenseDTOS = moduleLicensesCache.get(accountId);
    Edition edition = FREE;
    for (ModuleLicenseDTO moduleLicenseDTO : moduleLicenseDTOS) {
      // Checking if account is license type is trial, then don't consider its license edition
      if (moduleLicenseDTO.getLicenseType() == LicenseType.TRIAL) {
        continue;
      }
      if (moduleLicenseDTO.getEdition() == ENTERPRISE || moduleLicenseDTO.getEdition() == TEAM) {
        edition = moduleLicenseDTO.getEdition();
      }
    }
    return edition;
  }

  // We can use FF to figure out whether, or not to queue execution based on max limit.
  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(String accountId, String pipelineIdentifier) {
    try {
      Edition edition = getEdition(accountId);
      if (edition != COMMUNITY) {
        // Sending only accountId here because this setting only exists at account level
        long maxConcurrentExecutions = Long.parseLong(
            NGRestUtils
                .getResponse(ngSettingsClient.getSetting(
                    PipelineSettingsConstants.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS, accountId, null, null))
                .getValue());
        if (!pmsFeatureFlagService.isEnabled(
                accountId, FeatureName.DO_NOT_ENFORCE_LIMITS_ON_CONCURRENT_PIPELINE_EXECUTIONS)) {
          return shouldQueueInternal(accountId, pipelineIdentifier, maxConcurrentExecutions);
        }
      }
    } catch (Exception ex) {
      return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
    }
    return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
  }

  // There's no setting added in account resources for max pipeline creation. And this is added as ignored setting in
  // the pipeline settings and limits doc
  @Override
  public long getMaxPipelineCreationCount(String accountId) {
    try {
      Edition edition = getEdition(accountId);
      switch (edition) {
        case FREE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForFree()) {
            return orchestrationRestrictionConfiguration.getPipelineCreationRestriction().getFree();
          }
          break;
        case ENTERPRISE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise()) {
            return orchestrationRestrictionConfiguration.getPipelineCreationRestriction().getEnterprise();
          }
          break;
        case TEAM:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
            return orchestrationRestrictionConfiguration.getPipelineCreationRestriction().getTeam();
          }
          break;
        default:
          PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
      }
    } catch (Exception ex) {
      return Long.MAX_VALUE;
    }
    return Long.MAX_VALUE;
  }

  @Override
  public int getMaxConcurrencyBasedOnEdition(String accountId, long childCount) {
    int maxConcurrencyLimitBasedOnPlan = 20;
    try {
      Edition edition = getEdition(accountId);
      if (edition != COMMUNITY) {
        if (enforcementClientService.isEnforcementEnabled()) {
          Optional<RestrictionMetadataDTO> restrictionMetadataDTO = enforcementClientService.getRestrictionMetadata(
              FeatureRestrictionName.MAX_PARALLEL_STEP_IN_A_PIPELINE, accountId);
          if (restrictionMetadataDTO.isPresent()
              && restrictionMetadataDTO.get().getRestrictionType() == RestrictionType.STATIC_LIMIT) {
            StaticLimitRestrictionMetadataDTO staticLimitRestrictionDTO =
                (StaticLimitRestrictionMetadataDTO) restrictionMetadataDTO.get();
            maxConcurrencyLimitBasedOnPlan = staticLimitRestrictionDTO.getLimit().intValue();
          }
          if (childCount > maxConcurrencyLimitBasedOnPlan) {
            throw new InvalidRequestException(String.format(
                "Trying to run more than %s concurrent stages/steps. Please upgrade your plan or reduce concurrency",
                maxConcurrencyLimitBasedOnPlan));
          }
        }
      }
    } catch (EnforcementServiceConnectionException | WrongFeatureStateException e) {
      log.error("Got exception while talking to enforcement service, taking default limit of 100 for maxConcurrency");
    } catch (ExecutionException e) {
      return maxConcurrencyLimitBasedOnPlan;
    }
    return maxConcurrencyLimitBasedOnPlan;
  }

  private PlanExecutionSettingResponse shouldQueueInternal(String accountId, String pipelineIdentifier, long maxCount) {
    long runningExecutionsForGivenPipeline =
        planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId, pipelineIdentifier);
    if (runningExecutionsForGivenPipeline >= maxCount) {
      return PlanExecutionSettingResponse.builder().shouldQueue(true).useNewFlow(true).build();
    }
    return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(true).build();
  }
}
