/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SweepingOutputInstance;
import io.harness.expression.LateBindingMap;
import io.harness.kryo.KryoSerializer;

import software.wings.exception.SweepingOutputException;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
public class SweepingOutputFunctor extends LateBindingMap {
  transient SweepingOutputInquiryBuilder sweepingOutputInquiryBuilder;
  transient SweepingOutputService sweepingOutputService;
  transient KryoSerializer kryoSerializer;

  public synchronized Object output(String name) {
    SweepingOutputInstance sweepingOutputInstance =
        sweepingOutputService.find(sweepingOutputInquiryBuilder.name(name).build());
    if (sweepingOutputInstance == null) {
      throw new SweepingOutputException(format("Missing sweeping output %s", name));
    }

    if (sweepingOutputInstance.getValue() != null) {
      return sweepingOutputInstance.getValue();
    }

    return kryoSerializer.asInflatedObject(sweepingOutputInstance.getOutput());
  }

  @Override
  public synchronized Object get(Object key) {
    return output((String) key);
  }
}
