package io.harness.idp.environmentvariable.beans;

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
    private Long createdAt;
    private Long lastModifiedAt;
    private boolean isDeleted;
    private long deletedAt;
}
