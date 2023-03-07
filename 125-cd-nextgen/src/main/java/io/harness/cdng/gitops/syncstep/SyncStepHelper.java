package io.harness.cdng.gitops.syncstep;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationResource;
import io.harness.gitops.models.ApplicationSyncRequest;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class SyncStepHelper {
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

  public static ApplicationSyncRequest getSyncRequest(Application application, String targetRevision,
      SyncStepParameters syncStepParameters, boolean syncAllResources,
      List<ApplicationResource.Resource> outOfSyncResources) {
    ApplicationSyncRequest.ApplicationSyncRequestBuilder syncRequestBuilder = ApplicationSyncRequest.builder();
    syncRequestBuilder.dryRun(syncStepParameters.getDryRun().getValue());
    syncRequestBuilder.prune(syncStepParameters.getPrune().getValue());
    syncRequestBuilder.applicationName(application.getName());
    syncRequestBuilder.targetRevision(targetRevision);

    mapSyncStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncRetryStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncOptions(syncStepParameters, syncRequestBuilder);
    mapSyncResources(syncAllResources, outOfSyncResources, syncRequestBuilder);
    return syncRequestBuilder.build();
  }

  private static void mapSyncStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequest.ApplicationSyncRequestBuilder syncRequestBuilder) {
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
      SyncStepParameters syncStepParameters, ApplicationSyncRequest.ApplicationSyncRequestBuilder syncRequestBuilder) {
    SyncRetryStrategy syncRetryStrategy = syncStepParameters.getRetryStrategy();
    if (syncRetryStrategy != null) {
      // if retry is selected in the UI, all the below options should be populated, otherwise UI should throw the error
      // as same as the Sync UI in GitOps
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
      SyncStepParameters syncStepParameters, ApplicationSyncRequest.ApplicationSyncRequestBuilder syncRequestBuilder) {
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
    // TODO add logs
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
    return resources.stream()
        .filter(resource -> resource.getStatus() == SyncResources.OUT_OF_SYNC.getValue())
        .collect(Collectors.toList());
  }

  public static boolean isStaleApplication(ApplicationResource latestApplicationState) {
    return Boolean.TRUE.equals(latestApplicationState.getStale());
  }
}
