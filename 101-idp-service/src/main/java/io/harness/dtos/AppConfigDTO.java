package io.harness.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

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
