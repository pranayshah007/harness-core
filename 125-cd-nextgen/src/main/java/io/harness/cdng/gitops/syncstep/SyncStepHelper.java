/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import com.google.inject.Singleton;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncRequest.ApplicationSyncRequestBuilder;
import io.harness.gitops.models.ApplicationSyncRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
                .isSyncAllowed(true)
                   .build())
        .collect(Collectors.toList());
  }

  public static ApplicationSyncRequest getSyncRequest(
      Application application, SyncStepParameters syncStepParameters) {
    ApplicationSyncRequestBuilder syncRequestBuilder = ApplicationSyncRequest.builder();
    syncRequestBuilder.dryRun(syncStepParameters.getDryRun().getValue());
    syncRequestBuilder.prune(syncStepParameters.getPrune().getValue());
    syncRequestBuilder.applicationName(application.getName());
    syncRequestBuilder.targetRevision(application.getRevision());

    mapSyncStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncRetryStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncOptions(syncStepParameters, syncRequestBuilder);
    return syncRequestBuilder.build();
  }

  private static void mapSyncStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    ApplicationSyncRequest.SyncStrategyApply strategyApply =
        ApplicationSyncRequest.SyncStrategyApply.builder().force(syncStepParameters.getForceApply().getValue()).build();

    // if applyOnly is true => strategy is apply, else hook
    if (Boolean.TRUE.equals(syncStepParameters.getApplyOnly().getValue())) {
      syncRequestBuilder.strategy(ApplicationSyncRequest.SyncStrategy.builder().apply(strategyApply).build());
    } else {
      ApplicationSyncRequest.SyncStrategyHook strategyHook =
          ApplicationSyncRequest.SyncStrategyHook.builder().syncStrategyApply(strategyApply).build();
      syncRequestBuilder.strategy(ApplicationSyncRequest.SyncStrategy.builder().hook(strategyHook).build());
    }
  }

  private static void mapSyncRetryStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    SyncRetryStrategy syncRetryStrategy = syncStepParameters.getRetryStrategy();
    if (syncRetryStrategy != null) {
      syncRequestBuilder.retryStrategy(
          ApplicationSyncRequest.RetryStrategy.builder()
              .limit(syncRetryStrategy.getLimit().getValue())
              .backoff(ApplicationSyncRequest.Backoff.builder()
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
      ApplicationSyncRequest.ApplicationSyncRequestBuilder syncRequestBuilder) {
    // sync only out of sync resources
    if (!syncAllResources && isNotEmpty(outOfSyncResources)) {
      List<ApplicationSyncRequest.SyncOperationResource> resourcesToSync = new ArrayList<>();
      for (ApplicationResource.Resource resource : outOfSyncResources) {
        ApplicationSyncRequest.SyncOperationResource.SyncOperationResourceBuilder resourceBuilder =
            ApplicationSyncRequest.SyncOperationResource.builder();
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

  public static List<ApplicationResource.Resource> getOnlyOutOfSyncResources(
      List<ApplicationResource.Resource> resources) {
    return resources.stream().filter(resource -> OUT_OF_SYNC.equals(resource.getStatus())).collect(Collectors.toList());
  }

  public static boolean isStaleApplication(ApplicationResource latestApplicationState) {
    return Boolean.TRUE.equals(latestApplicationState.getStale());
  }

  public static List<Application> getApplicationsToBeManuallySynced(List<Application> applications) {
    return applications.stream().filter(application -> !application.isAutoSyncEnabled()).collect(Collectors.toList());
  }
}
