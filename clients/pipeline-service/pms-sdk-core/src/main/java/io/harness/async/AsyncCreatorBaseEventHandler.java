/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.async;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.execution.events.PmsCommonsBaseEventHandler;
import io.harness.pms.yaml.YamlField;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class AsyncCreatorBaseEventHandler<T extends Message, C extends AsyncCreatorContext>
    implements PmsCommonsBaseEventHandler<T> {
  @Inject public PmsGitSyncHelper pmsGitSyncHelper;
  @Inject public ExceptionManager exceptionManager;

  @NonNull protected abstract Map<String, String> extraLogProperties(T event);

  protected abstract Dependencies extractDependencies(T message);

  protected abstract C extractContext(T message);

  @Override
  public void handleEvent(T event, Map<String, String> metadataMap, long messageTimeStamp, long readTs) {
    try {
      AsyncCreatorResponse finalResponse =
          handleDependenciesRecursive(extractDependencies(event), extractContext(event));
      handleResult(event, finalResponse);
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex), ex);
      handleException(event, ex);
    }
  }

  protected abstract void handleResult(T event, AsyncCreatorResponse creatorResponse);

  private AsyncCreatorResponse handleDependenciesRecursive(Dependencies initialDependencies, C context) {
    // TODO: Add patch version before sending the response back
    AsyncCreatorResponse finalResponse = createNewAsyncCreatorResponse(context);
    if (EmptyPredicate.isEmpty(initialDependencies.getDependenciesMap())) {
      return finalResponse;
    }

    ByteString gitSyncBranchContext = context.getGitSyncBranchContext();

    try (PmsGitSyncBranchContextGuard ignore =
             pmsGitSyncHelper.createGitSyncBranchContextGuardFromBytes(gitSyncBranchContext, true)) {
      Dependencies dependencies = initialDependencies.toBuilder().build();
      while (!dependencies.getDependenciesMap().isEmpty()) {
        dependencies = handleDependencies(context, finalResponse, dependencies);
        removeInitialDependencies(dependencies, initialDependencies);
      }
    }

    if (finalResponse.getDependencies() != null
        && EmptyPredicate.isNotEmpty(finalResponse.getDependencies().getDependenciesMap())) {
      finalResponse.setDependencies(removeInitialDependencies(finalResponse.getDependencies(), initialDependencies));
    }
    return finalResponse;
  }

  protected abstract AsyncCreatorResponse createNewAsyncCreatorResponse(C context);

  public abstract Dependencies handleDependencies(C ctx, AsyncCreatorResponse finalResponse, Dependencies dependencies);

  protected abstract void handleException(T event, YamlField field, Exception ex);
  protected abstract void handleException(T event, Exception ex);

  private Dependencies removeInitialDependencies(Dependencies dependencies, Dependencies initialDependencies) {
    if (initialDependencies == null || EmptyPredicate.isEmpty(initialDependencies.getDependenciesMap())) {
      return dependencies;
    }
    if (dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return dependencies;
    }

    Dependencies.Builder builder = dependencies.toBuilder();
    initialDependencies.getDependenciesMap().keySet().forEach(builder::removeDependencies);
    return builder.build();
  }
}
