package io.harness.cdng.elastigroup;

import io.harness.annotations.dev.OwnedBy;
import lombok.Data;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

@OwnedBy(PIPELINE)
@Data
public abstract class InstancesSpecAbstractYaml {

    public String getType() {
        return null;
    };
}
