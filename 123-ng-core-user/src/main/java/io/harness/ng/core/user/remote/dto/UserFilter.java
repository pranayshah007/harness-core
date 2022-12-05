/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.remote.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@ApiModel(value = "UserFilter")
@OwnedBy(PL)
public class UserFilter {
  @Schema(
      description =
          "This string will be used to filter the results. Details of all the users having this string in their name or email address will be filtered.")
  private String searchTerm;
  @Schema(description = "Filter by User Identifiers") private Set<String> identifiers;
  @Schema(description = "Filter by User Emails") private Set<String> emails;
  @Builder.Default private ParentFilter parentFilter = ParentFilter.NO_PARENT_SCOPES;

  public ParentFilter getParentFilter() {
    return parentFilter == null ? ParentFilter.NO_PARENT_SCOPES : parentFilter;
  }

  public enum ParentFilter { NO_PARENT_SCOPES, INCLUDE_PARENT_SCOPES, STRICTLY_PARENT_SCOPES }
}
