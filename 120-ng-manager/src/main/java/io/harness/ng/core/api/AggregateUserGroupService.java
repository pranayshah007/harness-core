/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.UserGroupAggregateDTO;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface AggregateUserGroupService {
  PageResponse<UserGroupAggregateDTO> listAggregateUserGroups(@NotNull PageRequest pageRequest,
      @NotEmpty String accountIdentifier, String orgIdentifier, String projectIdentifier, String searchTerm,
      int userSize, UserGroupFilterType filterType);

  UserGroupAggregateDTO getAggregatedUserGroup(@NotEmpty String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotEmpty String userGroupIdentifier, ScopeDTO roleAssignmentScope);
}
