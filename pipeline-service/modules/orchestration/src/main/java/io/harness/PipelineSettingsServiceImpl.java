/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness;

import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.licensing.Edition.FREE;
import static io.harness.licensing.Edition.TEAM;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.remote.client.NGRestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;

@Singleton
public class PipelineSettingsServiceImpl implements PipelineSettingsService {
  @Inject PlanExecutionService planExecutionService;

  @Inject NgLicenseHttpClient ngLicenseHttpClient;

  @Inject OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;

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

  @VisibleForTesting
  protected Edition getEdition(String accountId) throws ExecutionException {
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

  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(String accountId, String pipelineIdentifier) {
    try {
      Edition edition = getEdition(accountId);
      switch (edition) {
        case FREE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForFree()) {
            return shouldQueueInternal(accountId, pipelineIdentifier,
                orchestrationRestrictionConfiguration.getPlanExecutionRestriction().getFree());
          }
          break;
        case ENTERPRISE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise()) {
            return shouldQueueInternal(accountId, pipelineIdentifier,
                orchestrationRestrictionConfiguration.getPlanExecutionRestriction().getEnterprise());
          }
          break;
        case TEAM:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
            return shouldQueueInternal(accountId, pipelineIdentifier,
                orchestrationRestrictionConfiguration.getPlanExecutionRestriction().getTeam());
          }
          break;
        default:
          PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
      }
    } catch (Exception ex) {
      return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
    }
    return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
  }

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
    try {
      Edition edition = getEdition(accountId);
      switch (edition) {
        case FREE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForFree()) {
            if (childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getFree()) {
              throw new InvalidRequestException(String.format(
                  "Trying to run more than %s concurrent stages/steps. Please upgrade your plan to Team (Paid) or reduce concurrency",
                  orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getFree()));
            }
            return (int) orchestrationRestrictionConfiguration.getMaxConcurrencyRestriction().getFree();
          }
          return 20;
        case ENTERPRISE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise()) {
            if (childCount
                > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getEnterprise()) {
              throw new InvalidRequestException(String.format(
                  "Trying to run more than %s concurrent stages/steps. Please contact sales if you want to run more",
                  orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getEnterprise()));
            }
            return (int) orchestrationRestrictionConfiguration.getMaxConcurrencyRestriction().getEnterprise();
          }
          return 100;
        case TEAM:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
            if (childCount > orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getTeam()) {
              throw new InvalidRequestException(String.format(
                  "Trying to run more than %s concurrent stages/steps. Please upgrade your plan to Enterprise (Paid) or reduce concurrency",
                  orchestrationRestrictionConfiguration.getTotalParallelismStopRestriction().getTeam()));
            }
            return (int) orchestrationRestrictionConfiguration.getMaxConcurrencyRestriction().getTeam();
          }
          return 50;
        default:
          return 20;
      }
    } catch (ExecutionException e) {
      return 20;
    }
  }
  @VisibleForTesting
  protected PlanExecutionSettingResponse shouldQueueInternal(
      String accountId, String pipelineIdentifier, long maxCount) {
    long runningExecutionsForGivenPipeline =
        planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountId, pipelineIdentifier);
    if (runningExecutionsForGivenPipeline >= maxCount) {
      return PlanExecutionSettingResponse.builder().shouldQueue(true).useNewFlow(true).build();
    }
    return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(true).build();
  }
}
