/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans.custom.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PIPELINE)
@Data
@Builder
@NoArgsConstructor
public class TriggeredByInfoAuditDetails {
  public String type;
  public String identifier;
  public Map<String, String> extraInfo;

  public TriggeredByInfoAuditDetails(String type, String identifier, Map<String, String> extraInfo) {
    this.type = type;
    this.identifier = identifier;
    this.extraInfo = extraInfo;
  }
}
