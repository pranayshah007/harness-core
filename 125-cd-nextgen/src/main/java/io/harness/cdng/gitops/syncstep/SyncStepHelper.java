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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SyncStepHelper {
  private static final String OUT_OF_SYNC = "OutOfSync";
  public static final String SYNC_RETRY_STRATEGY_DURATION_REGEX = "/^([\\d\\.]+[HMS])+$/i";
  public static final int NETWORK_CALL_RETRY_SLEEP_DURATION_MILLIS = 10;
  public static final int NETWORK_CALL_MAX_RETRY_ATTEMPTS = 3;
  public static final long STOP_BEFORE_STEP_TIMEOUT_SECS = 30;
  public static final long POLLER_SLEEP_SECS = 5;
  public static final String APPLICATION_REFRESH_TYPE = "normal";

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

  public static ApplicationSyncRequest getSyncRequest(Application application, SyncStepParameters syncStepParameters) {
    ApplicationSyncRequestBuilder syncRequestBuilder = ApplicationSyncRequest.builder();
    syncRequestBuilder.dryRun(toBoolean(syncStepParameters.getDryRun().getValue()));
    syncRequestBuilder.prune(toBoolean(syncStepParameters.getPrune().getValue()));
    syncRequestBuilder.applicationName(application.getName());
    syncRequestBuilder.targetRevision(application.getRevision());

    mapSyncStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncRetryStrategy(syncStepParameters, syncRequestBuilder);
    mapSyncOptions(syncStepParameters, syncRequestBuilder);
    return syncRequestBuilder.build();
  }

  public static boolean toBoolean(Object value) {
    if (value instanceof Boolean) {
      return (boolean) value;
    } else if (value instanceof String) {
      return Boolean.parseBoolean((String) value);
    } else {
      throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to boolean");
    }
  }

  public static int toNumber(Object obj) {
    if (obj instanceof Integer) {
      return (int) obj;
    } else if (obj instanceof Double) {
      return ((Double) obj).intValue();
    } else {
      throw new IllegalArgumentException("Cannot convert " + obj.getClass().getName() + " to integer");
    }
  }

  private static void mapSyncStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    SyncStrategyApply strategyApply =
        SyncStrategyApply.builder().force(toBoolean(syncStepParameters.getForceApply().getValue())).build();

    // if applyOnly is true => strategy is apply, else hook
    if (Boolean.TRUE.equals(toBoolean(syncStepParameters.getApplyOnly().getValue()))) {
      syncRequestBuilder.strategy(SyncStrategy.builder().apply(strategyApply).build());
    } else {
      SyncStrategyHook strategyHook = SyncStrategyHook.builder().syncStrategyApply(strategyApply).build();
      syncRequestBuilder.strategy(SyncStrategy.builder().hook(strategyHook).build());
    }
  }

  private static void mapSyncRetryStrategy(
      SyncStepParameters syncStepParameters, ApplicationSyncRequestBuilder syncRequestBuilder) {
    SyncRetryStrategy syncRetryStrategy = syncStepParameters.getRetryStrategy();
    if (syncRetryStrategy != null && syncRetryStrategy.getLimit().getValue() != null
        && syncRetryStrategy.getBaseBackoffDuration().getValue() != null
        && syncRetryStrategy.getMaxBackoffDuration().getValue() != null
        && syncRetryStrategy.getIncreaseBackoffByFactor().getValue() != null) {
      syncRequestBuilder.retryStrategy(
          RetryStrategy.builder()
              .limit(toNumber(syncRetryStrategy.getLimit().getValue()))
              .backoff(Backoff.builder()
                           .baseDuration(syncRetryStrategy.getBaseBackoffDuration().getValue())
                           .maxDuration(syncRetryStrategy.getMaxBackoffDuration().getValue())
                           .factor(toNumber(syncRetryStrategy.getIncreaseBackoffByFactor().getValue()))
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
    getSyncOptionAsString(SyncOptionsEnum.VALIDATE.getValue(),
        !toBoolean(requestSyncOptions.getSkipSchemaValidation().getValue()), items);

    getSyncOptionAsString(SyncOptionsEnum.CREATE_NAMESPACE.getValue(),
        toBoolean(requestSyncOptions.getAutoCreateNamespace().getValue()), items);
    getSyncOptionAsString(SyncOptionsEnum.PRUNE_LAST.getValue(),
        toBoolean(requestSyncOptions.getPruneResourcesAtLast().getValue()), items);
    getSyncOptionAsString(SyncOptionsEnum.APPLY_OUT_OF_SYNC_ONLY.getValue(),
        toBoolean(requestSyncOptions.getApplyOutOfSyncOnly().getValue()), items);
    getSyncOptionAsString(SyncOptionsEnum.PRUNE_PROPAGATION_POLICY.getValue(),
        requestSyncOptions.getPrunePropagationPolicy().getValue(), items);
    getSyncOptionAsString(
        SyncOptionsEnum.REPLACE.getValue(), toBoolean(requestSyncOptions.getReplaceResources().getValue()), items);

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
    return syncOptionKey + "=" + value;
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

  public static List<Application> getApplicationsToBeManuallySynced(List<Application> applications) {
    return applications.stream().filter(application -> !application.isAutoSyncEnabled()).collect(Collectors.toList());
  }
}
