package software.wings.service.impl.instance;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ArtifactEntitySummaryStats {
  private String entityId;
  private String entityName;
  private String entityVersion;
  private long lastDeployedAt;
  private String lastArtifactSourceName;
  private int count;
  private String lastArtifactBuildNum;
}