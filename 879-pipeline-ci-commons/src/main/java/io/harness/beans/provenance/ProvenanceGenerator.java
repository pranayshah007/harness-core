/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.provenance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.version.VersionInfoManager;

import com.google.api.client.util.DateTime;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenanceGenerator {
  @Inject private VersionInfoManager versionInfoManager;

  public ProvenancePredicate buildProvenancePredicate(ProvenanceBuilderData data) {
    Map<String, String> versionMap = new HashMap<>();
    versionMap.put("ci-manager", versionInfoManager.getFullVersion());
    if (EmptyPredicate.isNotEmpty(data.getPluginInfo())) {
      String[] plugin = data.getPluginInfo().split(":");
      versionMap.put(plugin[0], plugin[1]);
    }
    return ProvenancePredicate.builder()
        .buildDefinition(BuildDefinition.builder()
                             .buildType("https://developer.harness.io/docs/continuous-integration")
                             .internalParameters(InternalParameters.builder()
                                                     .accountId(data.getAccountId())
                                                     .pipelineExecutionId(data.getPipelineExecutionId())
                                                     .pipelineIdentifier(data.getPipelineIdentifier())
                                                     .build())
                             .build())
        .runDetails(RunDetails.builder()
                        .builder(ProvenanceBuilder.builder()
                                     .id("https://developer.harness.io/docs/continuous-integration")
                                     .version(versionMap)
                                     .build())
                        .metadata(BuildMetadata.builder()
                                      .invocationId(data.getStepExecutionId())
                                      .startedOn(new DateTime(data.getStartTime()).toStringRfc3339())
                                      .finishedOn(new DateTime(System.currentTimeMillis()).toStringRfc3339())
                                      .build())
                        .build())
        .build();
  }
}
