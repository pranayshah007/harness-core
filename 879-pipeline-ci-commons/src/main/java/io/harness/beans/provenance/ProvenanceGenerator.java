/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.provenance;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.version.VersionInfoManager;

import com.google.api.client.util.DateTime;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenanceGenerator {
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  public ProvenancePredicate buildProvenancePredicate(ProvenanceBuilderData data, Ambiance ambiance) {
    Map<String, String> versionMap = new HashMap<>();
    versionMap.put("ci-manager", versionInfoManager.getFullVersion());
    if (EmptyPredicate.isNotEmpty(data.getPluginInfo())) {
      String[] plugin = data.getPluginInfo().split(":");
      versionMap.put(plugin[0], plugin[1]);
    }

    CodeMetadata codeMetadata = getCodeMetadata(ambiance);
    TriggerMetadata triggerMetadata = getTriggerMetadata(ambiance);
    return ProvenancePredicate.builder()
        .buildDefinition(BuildDefinition.builder()
                             .buildType("https://developer.harness.io/docs/continuous-integration")
                             .internalParameters(InternalParameters.builder()
                                                     .accountId(data.getAccountId())
                                                     .pipelineExecutionId(data.getPipelineExecutionId())
                                                     .pipelineIdentifier(data.getPipelineIdentifier())
                                                     .build())
                             .externalParameters(ExternalParameters.builder()
                                                     .buildMetadata(data.getBuildMetadata())
                                                     .codeMetadata(codeMetadata)
                                                     .triggerMetadata(triggerMetadata)
                                                     .build())
                             .build())
        .runDetails(RunDetails.builder()
                        .builder(ProvenanceBuilder.builder()
                                     .id("https://developer.harness.io/docs/continuous-integration")
                                     .version(versionMap)
                                     .build())
                        .runDetailsMetadata(RunDetailsMetadata.builder()
                                                .invocationId(data.getStepExecutionId())
                                                .startedOn(new DateTime(data.getStartTime()).toStringRfc3339())
                                                .finishedOn(new DateTime(System.currentTimeMillis()).toStringRfc3339())
                                                .build())
                        .build())
        .build();
  }

  private CodeMetadata getCodeMetadata(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    if (!optionalSweepingOutput.isFound()) {
      return null;
    }
    CodebaseSweepingOutput codebaseSweeping = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();

    return new CodeMetadata(codebaseSweeping.getRepoUrl(), codebaseSweeping.getBranch(), codebaseSweeping.getPrNumber(),
        codebaseSweeping.getTag(), codebaseSweeping.getCommitSha());
  }

  private TriggerMetadata getTriggerMetadata(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      return null;
    }
    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    ExecutionSource executionSource = stageDetails.getExecutionSource();

    String triggerEvent = null;

    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.WEBHOOK) {
      WebhookExecutionSource webhookExecutionSource = (WebhookExecutionSource) executionSource;
      triggerEvent = String.valueOf(webhookExecutionSource.getWebhookEvent().getType());
    }

    String triggerBy = AmbianceUtils.getTriggerIdentifier(ambiance);
    String triggerType = String.valueOf(AmbianceUtils.getTriggerType(ambiance));
    return new TriggerMetadata(triggerType, triggerBy, triggerEvent);
  }
}
