package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
public class JwtVerificationApiKeyRequestWrapper {
  @Valid @NotNull private JwtVerificationApiKeyDTO jwtVerificationApiKey;
}
