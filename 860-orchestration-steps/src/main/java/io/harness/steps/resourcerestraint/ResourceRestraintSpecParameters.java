/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@TypeAlias("resourceRestraintSpecParameters")
@RecasterAlias("io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters")
public class ResourceRestraintSpecParameters implements SpecParameters {
  String name;
  @NotNull ParameterField<String> resourceUnit;
  @NotNull AcquireMode acquireMode;
  @NotNull int permits;
  @NotNull HoldingScope holdingScope;
}
