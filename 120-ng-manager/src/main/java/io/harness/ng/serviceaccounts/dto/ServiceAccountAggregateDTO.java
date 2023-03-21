/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;
import io.harness.serviceaccount.ServiceAccountDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PL)
@Schema(name = "ServiceAccountAggregate", description = "This contains the Service Account details and its metadata.")
public class ServiceAccountAggregateDTO {
  @NotNull ServiceAccountDTO serviceAccount;
  @Schema(description = "This is the time at which Service Account was created.") @NotNull Long createdAt;
  @Schema(description = "This is the time at which Service Account was last modified.") @NotNull Long lastModifiedAt;
  @Schema(description = "This is the total number of tokens in a Service Account.") int tokensCount;
  @Schema(description = "This is the list of Role Assignments for the Service Account.")
  List<RoleAssignmentMetadataDTO> roleAssignmentsMetadataDTO;
}
