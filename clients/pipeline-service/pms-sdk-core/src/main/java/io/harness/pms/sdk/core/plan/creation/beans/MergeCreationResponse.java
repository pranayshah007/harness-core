package io.harness.pms.sdk.core.plan.creation.beans;

import io.harness.pms.contracts.plan.CreationResponse;

import lombok.Builder;

@Builder(builderMethodName = "parentBuilder")
public class MergeCreationResponse {
  // Will make this method abstract. And various CreationResponse classes needs to implement ths toProto method.
  public CreationResponse toProto() {
    return CreationResponse.newBuilder().build();
  }
}
