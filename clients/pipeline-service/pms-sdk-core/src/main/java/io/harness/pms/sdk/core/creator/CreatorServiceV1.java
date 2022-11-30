package io.harness.pms.sdk.core.creator;

import io.harness.pms.contracts.plan.CreationRequest;
import io.harness.pms.contracts.plan.CreationResponse;
import io.harness.pms.contracts.plan.DependencyV1;
import io.harness.pms.sdk.core.plan.creation.beans.MergeCreationResponse;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class CreatorServiceV1 {
  public MergeCreationResponse resolveDependency(
      String currentYaml, YamlField field, CreatorContext ctx, Map<String, String> metadata) {
    return MergeCreationResponse.parentBuilder().build();
  }

  abstract public CreatorContext getContext(CreationRequest creationRequest);
  public CreationResponse mergeResponse() {
    return CreationResponse.newBuilder().build();
  }

  public void mergeCreationResponsesIntoFinalResponse(MergeCreationResponse finalResponse,
      List<MergeCreationResponse> creationResponses, Map<String, DependencyV1> originalDependencies) {}

  public Map<String, DependencyV1> getNewDependenciesFromCreationResponse(
      List<MergeCreationResponse> creationResponses) {
    return Collections.emptyMap();
  }

  public CreationResponse getMappedCreationResponseProto(MergeCreationResponse finalResponse) {
    return CreationResponse.newBuilder().build();
  }
}
