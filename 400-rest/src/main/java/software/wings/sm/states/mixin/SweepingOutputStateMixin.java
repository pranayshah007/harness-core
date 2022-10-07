/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.mixin;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance;
import io.harness.serializer.KryoSerializerWrapper;

import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface SweepingOutputStateMixin {
  String getSweepingOutputName();

  SweepingOutputInstance.Scope getSweepingOutputScope();

  KryoSerializerWrapper getKryoSerializer();

  default void handleSweepingOutput(
      SweepingOutputService sweepingOutputService, ExecutionContext context, Object data) {
    if (isEmpty(getSweepingOutputName())) {
      return;
    }

    final String renderedOutputName = context.renderExpression(getSweepingOutputName());

    final SweepingOutputInstance sweepingOutputInstance = context.prepareSweepingOutputBuilder(getSweepingOutputScope())
                                                              .name(renderedOutputName)
                                                              .output(getKryoSerializer().asDeflatedBytes(data))
                                                              .build();

    sweepingOutputService.save(sweepingOutputInstance);
  }
}
