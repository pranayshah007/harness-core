/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.PlanLogOutputStream;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class TerragruntPlanCliRequest extends AbstractTerragruntCliRequest {
  String tfPlanName;
  PlanLogOutputStream planOutputStream;
  boolean destroy;
}
