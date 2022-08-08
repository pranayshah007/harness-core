/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.utils.PmsConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

/**
 * Create the queue spec parameters using a fixed name, permits, and acquire mode.
 *
 * <ul>
 *     <li>name: {@link PmsConstants#QUEUING_RC_NAME}</li>
 *     <li>permits: {@link PmsConstants#QUEUING_RC_PERMITS}</li>
 *     <li>acquireMode: {@link AcquireMode#ENSURE} (FIFO)</li>
 * </ul>
 *
 * <p>It is not required to add {@code JsonIgnore} annotation in getter methods.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor
@TypeAlias("queueSpecParameters")
@RecasterAlias("io.harness.steps.resourcerestraint.QueueSpecParameters")
public class QueueSpecParameters implements IResourceRestraintSpecParameters {
  @NotNull ParameterField<String> key;
  @NotNull HoldingScope scope;

  @Override
  public HoldingScope getHoldingScope() {
    return this.scope;
  }

  @Override
  public ParameterField<String> getResourceUnit() {
    return key;
  }

  // --
  // FIXED RETURN VALUES

  @Override
  public AcquireMode getAcquireMode() {
    return AcquireMode.ENSURE;
  }

  @Override
  public String getName() {
    return PmsConstants.QUEUING_RC_NAME;
  }

  @Override
  public int getPermits() {
    return PmsConstants.QUEUING_RC_PERMITS;
  }
}
