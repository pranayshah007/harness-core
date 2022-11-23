package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfInfraMappingDataResult {
  private List<String> organizations;
  private List<String> spaces;
  private List<String> routeMaps;
  private Integer runningInstanceCount;
}
