package io.harness.plancreator;

import io.harness.pms.pipeline.filter.PipelineFilter;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PipelineServiceFilter implements PipelineFilter {
  public static final String APPROVAL = "approval";
  Set<String> stageTypes;
  int featureFlagStepCount;

  public void mergeStageTypes(Set<String> stageTypes) {
    if (stageTypes == null) {
      return;
    }
    if (this.stageTypes == null) {
      this.stageTypes = new HashSet<>();
    } else if (!(this.stageTypes instanceof HashSet)) {
      this.stageTypes = new HashSet<>(this.stageTypes);
    }

    this.stageTypes.addAll(stageTypes);
  }
}
