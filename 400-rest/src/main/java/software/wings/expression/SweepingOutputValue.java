/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutputInstance;
import io.harness.expression.LateBindingValue;
import io.harness.kryo.KryoSerializer;

import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;

import lombok.Builder;

@OwnedBy(CDC)
@Builder
public class SweepingOutputValue implements LateBindingValue {
  private SweepingOutputService sweepingOutputService;
  private KryoSerializer kryoSerializer;
  private SweepingOutputInquiry sweepingOutputInquiry;

  @Override
  public Object bind() {
    SweepingOutputInstance sweepingOutputInstance = sweepingOutputService.find(sweepingOutputInquiry);
    if (sweepingOutputInstance == null) {
      return null;
    }

    if (sweepingOutputInstance.getValue() != null) {
      return sweepingOutputInstance.getValue();
    }

    return kryoSerializer.asInflatedObject(sweepingOutputInstance.getOutput());
  }
}
