/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.aws.sam.DownloadManifestsStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;

import com.google.inject.Inject;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;

  @Inject private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Inject private AwsSamPluginInfoProviderHelper awsSamPluginInfoProviderHelper;

  @Inject private DownloadManifestsStepHelper downloadManifestsStepHelper;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    return null;
  }

  @Override
  public PluginCreationResponseList getPluginInfoList(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    return downloadManifestsStepHelper.getPluginInfoList(request, usedPorts, ambiance);
  }

  @Override
  public boolean isSupported(String stepType) {
    return stepType.equals(StepSpecTypeConstants.DOWNLOAD_MANIFESTS);
  }

  @Override
  public boolean willReturnMultipleContainers() {
    return true;
  }
}
