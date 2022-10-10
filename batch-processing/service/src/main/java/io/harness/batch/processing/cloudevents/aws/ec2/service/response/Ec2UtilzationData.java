package io.harness.batch.processing.cloudevents.aws.ec2.service.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Ec2UtilzationData {
    private String instanceId;
    private List<MetricValue> metricValues;
}
