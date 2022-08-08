/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
@Schema(name = "UserGroupFilter", description = "This is the view of the UserGroupFilter entity defined in Harness")
public class UserGroupFilterDTO {
  @Schema(description = "Filter by the internal database ids of user group") Set<String> databaseIdFilter;
  @Schema(description = "Filter by the user group identifier") Set<String> identifierFilter;
  @Schema(description = "Filter by the users present in the user group") Set<String> userIdentifierFilter;
  @Schema(description = "Filter by account using account identifier", required = true) String accountIdentifier;
  @Schema(description = "Filter by organization using account identifier") String orgIdentifier;
  @Schema(description = "Filter by project using account identifier") String projectIdentifier;
  @Schema(description = "Filter by search term matching entities by name/identifier") String searchTerm;
  @Schema(description = "Filter by user group filterType")
  @Builder.Default
  UserGroupFilterType filterType = UserGroupFilterType.EXCLUDE_INHERITED_GROUPS;
}
