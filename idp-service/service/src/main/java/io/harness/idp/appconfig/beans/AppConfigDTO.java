package io.harness.idp.appconfig.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(HarnessTeam.IDP)
@Getter
@Builder
public class AppConfigDTO {
    private String accountIdentifier;
    private Long createdAt;
    private Long lastModifiedAt;
    private boolean isDeleted;
    private long deletedAt;
}
