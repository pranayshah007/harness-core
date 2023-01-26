package io.harness.idp.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(HarnessTeam.IDP)
@Getter
@Builder
public class EnvironmentVariableDTO {
    private String envName;
    private String secretIdentifier;
    private String accountIdentifier;
}
