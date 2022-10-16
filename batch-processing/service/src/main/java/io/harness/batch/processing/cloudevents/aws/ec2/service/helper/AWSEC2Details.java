package io.harness.batch.processing.cloudevents.aws.ec2.service.helper;

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
}
