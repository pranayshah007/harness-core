package io.harness.delegate.beans.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfApplicationDetailResult {
    private String name;
    private String guid;
    private String organization;
    private String space;
    private List<String> instanceIndices;
}
