package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.mongodb.morphia.annotations.Id;

@Data
@NoArgsConstructor
@OwnedBy(DX)
public class CompareEnvironmentAggregationInfo {
  @Id private ID _id;
  private String serviceId;
  private List<ServiceInfoSummary> serviceInfoSummaries;

  @Data
  @NoArgsConstructor
  public static final class ID {
    private String serviceId;
    private String envId;
    private String lastArtifactBuildNum;
    private String serviceName;
    private String lastWorkflowExecutionId;
    private String lastWorkflowExecutionName;
    private String infraMappingId;
    private String infraMappingName;
  }
}
