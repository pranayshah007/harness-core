package io.harness.batch.processing.cloudevents.aws.ec2.service.helper;

import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
public class AWSEC2Details {
  private String instanceId;
  private String region;

  @Builder
  public AWSEC2Details(String instanceId, String region) {
    this.instanceId = instanceId;
    this.region = region;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AWSEC2Details) {
      if (((AWSEC2Details) o).instanceId.equals(instanceId) && ((AWSEC2Details) o).region.equals(region)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.instanceId);
  }
}
