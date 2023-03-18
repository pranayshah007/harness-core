/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness;

import static io.harness.CreditType.CreditVisibility.PUBLIC;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HarnessTeam.PLG)
public enum CreditType {
  @JsonProperty("PAID") PAID("Paid Credits", PUBLIC),
  @JsonProperty("FREE") FREE("Free Credits", PUBLIC);

  String displayName;
  CreditVisibility visibility;

  enum CreditVisibility { INTERNAL, PUBLIC }

  CreditType(String displayName, CreditVisibility visibility) {
    this.displayName = displayName;
    this.visibility = visibility;
  }
}
