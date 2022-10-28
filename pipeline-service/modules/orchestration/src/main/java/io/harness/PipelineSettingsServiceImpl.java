package io.harness;

import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.licensing.Edition.FREE;
import static io.harness.licensing.Edition.TEAM;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.remote.client.NGRestUtils;

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
          .expireAfterWrite(10, TimeUnit.DAYS)
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
      if (moduleLicenseDTO.getEdition() == ENTERPRISE || moduleLicenseDTO.getEdition() == TEAM) {
        edition = moduleLicenseDTO.getEdition();
      }
    }
    return edition;
  }

  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    try {
      Edition edition = getEdition(accountId);
      switch (edition) {
        case FREE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForFree()) {
            return shouldQueueInternal(accountId, orgId, projectId, pipelineIdentifier,
                orchestrationRestrictionConfiguration.getPlanExecutionRestriction().getFree());
          }
          break;
        case ENTERPRISE:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForEnterprise()) {
            return shouldQueueInternal(accountId, orgId, projectId, pipelineIdentifier,
                orchestrationRestrictionConfiguration.getPlanExecutionRestriction().getEnterprise());
          }
          break;
        case TEAM:
          if (orchestrationRestrictionConfiguration.isUseRestrictionForTeam()) {
            return shouldQueueInternal(accountId, orgId, projectId, pipelineIdentifier,
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

  private PlanExecutionSettingResponse shouldQueueInternal(
      String accountId, String orgId, String projectId, String pipelineIdentifier, long count) {
    long runningExecutionsForGivenPipeline =
        planExecutionService.findRunningExecutionsForGivenPipeline(accountId, orgId, projectId, pipelineIdentifier);
    if (runningExecutionsForGivenPipeline >= count) {
      return PlanExecutionSettingResponse.builder().shouldQueue(true).useNewFlow(true).build();
    }
    return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(true).build();
  }
}
