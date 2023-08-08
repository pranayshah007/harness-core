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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
public class AllowList {
  @JsonProperty("allow_list") AllowListItem allowListItem;

  @Data
  @Builder
  public static class AllowListItem implements EnforcementListItem {
    List<Supplier> suppliers;
    List<AllowLicense> licenses;
    List<String> purls;

    @Override
    public String getPackageName() {
      return suppliers.get(0).name;
    }

    @Override
    public PolicyType getType() {
      return PolicyType.ALLOW_LIST;
    }
  }

  @Getter
  @AllArgsConstructor
  public enum AllowListRuleType {
    ALLOW_LICENSE_ITEM(),
    ALLOW_SUPPLIER_ITEM(),
    ALLOW_PURL_ITEM()
  }
}
