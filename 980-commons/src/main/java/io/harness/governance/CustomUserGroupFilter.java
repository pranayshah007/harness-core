/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.governance;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("CUSTOM")
@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDC)
@JsonInclude(NON_NULL)
public class CustomUserGroupFilter extends UserGroupFilter {
  private List<String> userGroups;

  @JsonCreator
  @Builder
  public CustomUserGroupFilter(@JsonProperty("filterType") BlackoutWindowFilterType userGroupFilterType,
      @JsonProperty("userGroups") List<String> userGroups) {
    super(userGroupFilterType);
    this.userGroups = userGroups;
  }
}
