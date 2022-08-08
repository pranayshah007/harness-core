/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.openapi.ApiClient;
import io.harness.cf.openapi.api.DefaultApi;
import io.harness.cf.openapi.model.Clause;
import io.harness.cf.openapi.model.FeatureState;
import io.harness.cf.openapi.model.PatchInstruction;
import io.harness.cf.openapi.model.Serve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@OwnedBy(HarnessTeam.CF)
public class CFApi extends DefaultApi {
  public CFApi() {}

  public CFApi(ApiClient apiClient) {
    super(apiClient);
  }

  public List<PatchInstruction> getFeatureFlagRulesForTargetingAccounts(Collection<String> accountIDs) {
    List<PatchInstruction> patchInstructions = new ArrayList<>();
    List<String> accountIDList = new ArrayList<>(accountIDs);
    AddTargetToVariationMapParams params = new AddTargetToVariationMapParams("true", accountIDList);
    PatchInstruction patchInstruction =
        PatchInstruction.builder().kind("addTargetsToVariationTargetMap").parameters(params).build();
    patchInstructions.add(patchInstruction);

    return patchInstructions;
  }

  public PatchInstruction removeRule(String ruleID) {
    return PatchInstruction.builder().kind("removeRule").parameters(new RemoveRuleParam(ruleID)).build();
  }

  public PatchInstruction getFeatureFlagDefaultServePatchInstruction(boolean variation) {
    String variationString;
    if (variation) {
      variationString = "true";
    } else {
      variationString = "false";
    }

    return PatchInstruction.builder()
        .kind("updateDefaultServe")
        .parameters(new UpdateDefaultServeParams(variationString))
        .build();
  }

  public PatchInstruction getFeatureFlagOnPatchInstruction(boolean state) {
    String stateString;
    if (state) {
      stateString = FeatureState.ON.getValue();
    } else {
      stateString = FeatureState.OFF.getValue();
    }

    return PatchInstruction.builder()
        .kind("setFeatureFlagState")
        .parameters(new FeatureFlagStateParams(stateString))
        .build();
  }

  public PatchInstruction getAddTargetToVariationMapParams(String variation, List<String> targets) {
    return PatchInstruction.builder()
        .kind("addTargetsToVariationTargetMap")
        .parameters(new AddTargetToVariationMapParams(variation, targets))
        .build();
  }

  public PatchInstruction getRemoveTargetToVariationMapParams(String variation, List<String> targets) {
    return PatchInstruction.builder()
        .kind("removeTargetsToVariationTargetMap")
        .parameters(new RemoveTargetToVariationMapParams(variation, targets))
        .build();
  }

  public PatchInstruction getAddSegmentToVariationMapParams(String variation, List<String> segments) {
    return PatchInstruction.builder()
        .kind("addSegmentToVariationTargetMap")
        .parameters(new AddSegmentToVariationMapParams(variation, segments))
        .build();
  }

  public PatchInstruction getRemoveSegmentToVariationMapParams(String variation, List<String> segments) {
    return PatchInstruction.builder()
        .kind("removeSegmentToVariationTargetMap")
        .parameters(new RemoveSegmentToVariationMapParams(variation, segments))
        .build();
  }

  public PatchInstruction addPercentageRollout(String uuid, int priority, Serve serve, List<Clause> clauses) {
    return PatchInstruction.builder()
        .kind("addRule")
        .parameters(AddRuleParam.newPercentageRollout(uuid, priority, serve, clauses))
        .build();
  }

  public PatchInstruction updatePercentageRollout(String uuid, Serve serve) {
    return PatchInstruction.builder()
        .kind("updateRule")
        .parameters(UpdateRuleParam.updatePercentageRollout(uuid, serve))
        .build();
  }

  public PatchInstruction setOnVariation(String variation) {
    return PatchInstruction.builder()
        .kind("updateDefaultServe")
        .parameters(new UpdateDefaultServeParams(variation))
        .build();
  }

  public PatchInstruction setOffVariation(String variation) {
    return PatchInstruction.builder()
        .kind("updateOffVariation")
        .parameters(new UpdateDefaultServeParams(variation))
        .build();
  }
}
