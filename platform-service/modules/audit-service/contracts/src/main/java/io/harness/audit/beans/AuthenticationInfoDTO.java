/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.AuditCommonConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Data
@Builder
@JsonInclude(NON_NULL)
@FieldNameConstants(innerTypeName = "AuthenticationInfoKeys")
@Schema(name = "AuthenticationInfo", description = "This has the AuthenticationInfo details defined in Harness.")
public class AuthenticationInfoDTO {
  @NotNull @Valid Principal principal;
  @Schema(description = "labels") @Size(max = 5) Map<String, String> labels;

  public static AuthenticationInfoDTO fromSecurityPrincipal(io.harness.security.dto.Principal principal) {
    if (principal == null) {
      return null;
    }
    switch (principal.getType()) {
      case USER:
        UserPrincipal userPrincipal = (UserPrincipal) principal;
        Map<String, String> labels = new HashMap<>();
        if (isNotEmpty(userPrincipal.getUsername())) {
          labels.put(AuditCommonConstants.USERNAME, userPrincipal.getUsername());
        }
        if (isNotEmpty(userPrincipal.getName())) {
          labels.put(AuditCommonConstants.USER_ID, userPrincipal.getName());
        }
        return AuthenticationInfoDTO.builder()
            .principal(Principal.fromSecurityPrincipal(principal))
            .labels(labels)
            .build();
      case SERVICE_ACCOUNT:
      case API_KEY:
      case SERVICE:
        return AuthenticationInfoDTO.builder().principal(Principal.fromSecurityPrincipal(principal)).build();
      default:
        throw new InvalidArgumentsException(String.format("Unknown principal type %s", principal.getType()));
    }
  }
}
