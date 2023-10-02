/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans;

import io.harness.ssca.enforcement.EnforcementListItem;
import io.harness.ssca.enforcement.constants.PolicyType;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DenyList {
  @JsonProperty("deny_list") List<DenyListItem> denyListItems;

  @Data
  @Builder
  public static class DenyListItem implements EnforcementListItem {
    String name;
    String version;
    String supplier;
    String license;
    String purl;

    @Override
    public String getPackageName() {
      return this.name;
    }

    @Override
    public PolicyType getType() {
      return PolicyType.DENY_LIST;
    }
  }
}
