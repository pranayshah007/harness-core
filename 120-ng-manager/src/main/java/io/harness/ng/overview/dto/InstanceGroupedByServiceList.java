package io.harness.ng.overview.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstanceGroupedByServiceList {
  List<InstanceGroupedByService> instanceGroupedByServices;

  @Value
  @Builder
  public static class InstanceGroupedByService {
    String serviceId;
    String serviceName;
    InstanceGroupedByArtifactList instanceGroupedByArtifactList;
  }
}
