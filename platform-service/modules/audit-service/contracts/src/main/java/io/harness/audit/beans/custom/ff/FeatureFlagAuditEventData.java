/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans.custom.ff;

import static io.harness.annotations.dev.HarnessTeam.CF;
import static io.harness.audit.beans.custom.AuditEventDataTypeConstants.FEATURE_FLAG_AUDIT_EVENT_DATA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEventData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
@OwnedBy(CF)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(FEATURE_FLAG_AUDIT_EVENT_DATA)
@TypeAlias(FEATURE_FLAG_AUDIT_EVENT_DATA)
public class FeatureFlagAuditEventData extends AuditEventData {
  String environment;
  @Builder
  public FeatureFlagAuditEventData(String environment) {
    this.environment = environment;
    this.type = FEATURE_FLAG_AUDIT_EVENT_DATA;
  }
}
