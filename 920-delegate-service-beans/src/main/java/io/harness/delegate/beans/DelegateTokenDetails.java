/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.security.dto.Principal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateTokenDetails {
  private String uuid;
  private String accountId;
  private String name;
  private EmbeddedUser createdBy;
  private Principal createdByNgUser;
  private long createdAt;
  private DelegateTokenStatus status;
  @Schema(
      description =
          "Value of delegate token. This is only populated when fetching delegate token by name or the user has edit delegate permission.")
  private String value;
  private String ownerIdentifier;
  private long revokeAfter;
}
