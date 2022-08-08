/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans.custom.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.audit.beans.custom.AuditEventDataTypeConstants.TEMPLATE_AUDIT_EVENT_DATA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.AuditEventData;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(TEMPLATE_AUDIT_EVENT_DATA)
@TypeAlias(TEMPLATE_AUDIT_EVENT_DATA)
public class TemplateEventData extends AuditEventData {
  String comments;
  String templateUpdateEventType;

  @Builder
  public TemplateEventData(String comments, String templateUpdateEventType) {
    this.comments = comments;
    this.templateUpdateEventType = templateUpdateEventType;
    this.type = TEMPLATE_AUDIT_EVENT_DATA;
  }
}
