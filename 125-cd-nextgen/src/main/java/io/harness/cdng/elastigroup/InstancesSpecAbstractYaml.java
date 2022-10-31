package io.harness.cdng.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
public abstract class InstancesSpecAbstractYaml {

    public String getType() {
        return null;
    };
}
