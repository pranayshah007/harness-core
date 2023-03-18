/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationResource.Resource;
import io.harness.gitops.models.ApplicationSyncRequest;
import io.harness.gitops.models.ApplicationSyncRequest.ApplicationSyncRequestBuilder;
import io.harness.gitops.models.ApplicationSyncRequest.Backoff;
import io.harness.gitops.models.ApplicationSyncRequest.RetryStrategy;
import io.harness.gitops.models.ApplicationSyncRequest.SyncOperationResource;
import io.harness.gitops.models.ApplicationSyncRequest.SyncOperationResource.SyncOperationResourceBuilder;
import io.harness.gitops.models.ApplicationSyncRequest.SyncStrategy;
import io.harness.gitops.models.ApplicationSyncRequest.SyncStrategyApply;
import io.harness.gitops.models.ApplicationSyncRequest.SyncStrategyHook;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class SyncStepHelper {
  private static final String OUT_OF_SYNC = "OutOfSync";

  public static List<Application> getApplicationsToBeSynced(
      List<AgentApplicationTargets> agentApplicationTargetsToBeSynced) {
    return agentApplicationTargetsToBeSynced.stream()
        .map(application
            -> Application.builder()
                   .agentIdentifier(application.getAgentId().getValue())
                   .name(application.getApplicationName().getValue())
                   .build())
        .collect(Collectors.toList());
  }

  public static ApplicationSyncRequest getSyncRequest(
      Application application, String targetRevision, SyncStepParameters syncStepParameters) {
    ApplicationSyncRequestBuilder syncRequestBuilder = ApplicationSyncRequest.builder();
    syncRequestBuilder.dryRun(syncStepParameters.getDryRun().getValue());
    syncRequestBuilder.prune(syncStepParameters.getPrune().getValue());
    syncRequestBuilder.applicationName(application.getName());
    syncRequestBuilder.targetRevision(targetRevision);

    mapSyncStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncRetryStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncOptions(syncStepParameters, syncRequestBuilder);
    return syncRequestBuilder.build();
  }

  private static void mapSyncStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    SyncStrategyApply strategyApply =
        SyncStrategyApply.builder().force(syncStepParameters.getForceApply().getValue()).build();

    // if applyOnly is true => strategy is apply, else hook
    if (Boolean.TRUE.equals(syncStepParameters.getApplyOnly().getValue())) {
      syncRequestBuilder.strategy(SyncStrategy.builder().apply(strategyApply).build());
    } else {
      SyncStrategyHook strategyHook = SyncStrategyHook.builder().syncStrategyApply(strategyApply).build();
      syncRequestBuilder.strategy(SyncStrategy.builder().hook(strategyHook).build());
    }
  }

  private static void mapSyncRetryStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    SyncRetryStrategy syncRetryStrategy = syncStepParameters.getRetryStrategy();
    if (syncRetryStrategy != null) {
      // if retry is selected in the UI, all the below options should be populated, otherwise UI should throw the error
      // as same as the Sync UI in GitOps
      syncRequestBuilder.retryStrategy(
          RetryStrategy.builder()
              .limit(syncRetryStrategy.getLimit().getValue())
              .backoff(Backoff.builder()
                           .baseDuration(syncRetryStrategy.getBaseBackoffDuration().getValue())
                           .maxDuration(syncRetryStrategy.getMaxBackoffDuration().getValue())
                           .factor(syncRetryStrategy.getIncreaseBackoffByFactor().getValue())
                           .build())
              .build());
    }
  }

  private static void mapSyncOptions(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    ApplicationSyncRequest.SyncOptions.SyncOptionsBuilder syncOptionsBuilder =
        ApplicationSyncRequest.SyncOptions.builder();

    List<String> items = new ArrayList<>();
    SyncOptions requestSyncOptions = syncStepParameters.getSyncOptions();

    // if skipSchemaValidation is selected in UI, the Validate parameter to GitOps service should be false
    getSyncOptionAsString(
        SyncOptionsEnum.VALIDATE.getValue(), !requestSyncOptions.getSkipSchemaValidation().getValue(), items);

    getSyncOptionAsString(
        SyncOptionsEnum.CREATE_NAMESPACE.getValue(), requestSyncOptions.getAutoCreateNamespace().getValue(), items);
    getSyncOptionAsString(
        SyncOptionsEnum.PRUNE_LAST.getValue(), requestSyncOptions.getPruneResourcesAtLast().getValue(), items);
    getSyncOptionAsString(SyncOptionsEnum.APPLY_OUT_OF_SYNC_ONLY.getValue(),
        requestSyncOptions.getApplyOutOfSyncOnly().getValue(), items);
    getSyncOptionAsString(SyncOptionsEnum.PRUNE_PROPAGATION_POLICY.getValue(),
        requestSyncOptions.getPrunePropagationPolicy().getValue(), items);
    getSyncOptionAsString(
        SyncOptionsEnum.REPLACE.getValue(), requestSyncOptions.getReplaceResources().getValue(), items);

    syncRequestBuilder.syncOptions(syncOptionsBuilder.items(items).build());
  }

  private static void mapSyncResources(boolean syncAllResources, List<ApplicationResource.Resource> outOfSyncResources,
      ApplicationSyncRequestBuilder syncRequestBuilder) {
    // sync only out of sync resources
    if (!syncAllResources && isNotEmpty(outOfSyncResources)) {
      List<SyncOperationResource> resourcesToSync = new ArrayList<>();
      for (ApplicationResource.Resource resource : outOfSyncResources) {
        SyncOperationResourceBuilder resourceBuilder = SyncOperationResource.builder();
        resourceBuilder.group(resource.getGroup())
            .kind(resource.getKind())
            .name(resource.getName())
            .namespace(resource.getNamespace());
        resourcesToSync.add(resourceBuilder.build());
      }
      syncRequestBuilder.resources(resourcesToSync);
    }
  }

  private static void getSyncOptionAsString(String syncOptionKey, String value, List<String> items) {
    items.add(getSyncOptionItem(syncOptionKey, value));
  }

  private static String getSyncOptionItem(String syncOptionKey, String value) {
    return "\"" + syncOptionKey + "=" + value + "\"";
  }

  private static void getSyncOptionAsString(String syncOptionKey, boolean value, List<String> items) {
    items.add(getSyncOptionItem(syncOptionKey, String.valueOf(value)));
  }

  public static List<Resource> getOnlyOutOfSyncResources(List<Resource> resources) {
    return resources.stream().filter(resource -> OUT_OF_SYNC.equals(resource.getStatus())).collect(Collectors.toList());
  }

  public static boolean isStaleApplication(ApplicationResource latestApplicationState) {
    return Boolean.TRUE.equals(latestApplicationState.getStale());
  }
}
