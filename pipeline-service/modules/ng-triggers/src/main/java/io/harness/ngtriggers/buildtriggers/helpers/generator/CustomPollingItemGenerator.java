/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.polling.contracts.CustomPayload;
import io.harness.polling.contracts.NGVariable;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.Type;
import io.harness.yaml.core.variables.NGVariableTrigger;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(CDC)
public class CustomPollingItemGenerator implements PollingItemGenerator {
  @Inject BuildTriggerHelper buildTriggerHelper;

  @Override
  public PollingItem generatePollingItem(BuildTriggerOpsData buildTriggerOpsData) {
    NGTriggerEntity ngTriggerEntity = buildTriggerOpsData.getTriggerDetails().getNgTriggerEntity();
    PollingItem.Builder builder = getBaseInitializedPollingItem(ngTriggerEntity, buildTriggerOpsData);
    String script = buildTriggerHelper.validateAndFetchFromJsonNode(
        buildTriggerOpsData, "spec.scripts.fetchAllArtifacts.spec.source.spec.script");
    String artifactsArrayPath = buildTriggerHelper.validateAndFetchFromJsonNode(
        buildTriggerOpsData, "spec.scripts.fetchAllArtifacts.artifactsArrayPath");
    String versionPath = buildTriggerHelper.validateAndFetchFromJsonNode(
        buildTriggerOpsData, "spec.scripts.fetchAllArtifacts.versionPath");
    if (EmptyPredicate.isEmpty(script)) {
      script = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.script");
    }
    if (EmptyPredicate.isEmpty(versionPath)) {
      versionPath = buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.versionPath");
    }
    if (EmptyPredicate.isEmpty(artifactsArrayPath)) {
      artifactsArrayPath =
          buildTriggerHelper.validateAndFetchFromJsonNode(buildTriggerOpsData, "spec.artifactsArrayPath");
    }

    List<NGVariableTrigger> inputs =
        buildTriggerHelper.validateAndFetchListFromJsonNode(buildTriggerOpsData, "spec.inputs");
    return builder
        .setPollingPayloadData(PollingPayloadData.newBuilder()
                                   .setType(Type.CUSTOM_ARTIFACT)
                                   .setCustomPayload(CustomPayload.newBuilder()
                                                         .setArtifactsArrayPath(artifactsArrayPath)
                                                         .setVersionPath(versionPath)
                                                         .addAllNgVariable(mapToNgVariable(inputs))
                                                         .setScript(script)
                                                         .build())
                                   .build())
        .build();
  }

  public List<NGVariable> mapToNgVariable(List<NGVariableTrigger> variables) {
    List<NGVariable> inputs = new ArrayList<>();
    for (NGVariableTrigger variable : variables) {
      inputs.add(NGVariable.newBuilder()
                     .setName(variable.getName())
                     .setType(variable.getType().name())
                     .setValue(variable.getValue())
                     .build());
    }
    return inputs;
  }
}
