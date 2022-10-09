package io.harness.batch.processing.cloudevents.aws.ec2.service.response;

import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response.MetricValue;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Ec2UtilzationData {
    private String clusterArn;
    private String clusterName;
    private String serviceArn;
    private String serviceName;
    private String clusterId;
    private String settingId;
    private List<MetricValue> metricValues;
}
