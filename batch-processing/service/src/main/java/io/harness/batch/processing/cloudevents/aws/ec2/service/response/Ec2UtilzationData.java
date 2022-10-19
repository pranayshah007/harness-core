package io.harness.batch.processing.cloudevents.aws.ec2.service.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Ec2UtilzationData {
  private String instanceId;
  private List<MetricValue> metricValues;
}
