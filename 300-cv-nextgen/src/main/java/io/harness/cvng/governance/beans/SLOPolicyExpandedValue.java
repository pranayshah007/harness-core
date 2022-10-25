/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.governance.beans;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.governance.beans.ExpansionKeysConstants.SLO_POLICY_EXPANSION_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.serializer.JsonUtils;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CV)
@Builder
@Slf4j
public class SLOPolicyExpandedValue implements ExpandedValue {
  SLOPolicyDTO sloPolicyDTO;

  @Override
  public String getKey() {
    return SLO_POLICY_EXPANSION_KEY;
  }

  @Override
  public String toJson() {
    return JsonUtils.asJson(sloPolicyDTO);
  }
}
