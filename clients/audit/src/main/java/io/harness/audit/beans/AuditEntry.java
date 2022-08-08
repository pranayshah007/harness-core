/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Getter
@Builder
public class AuditEntry {
  @NotNull @NotBlank String insertId;
  @NotNull @Valid ResourceDTO resource;
  @NotNull @Valid ResourceScopeDTO resourceScope;
  @NotNull Action action;
  @NotNull ModuleType module;
  AuditEventData auditEventData;
  Environment environment;
  String oldYaml;
  String newYaml;
  long timestamp;
}
