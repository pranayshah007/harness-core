/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.advisers.rollback;

import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.advisers.ProceedWithDefaultAdvise;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.pms.sdk.core.adviser.AdvisingEvent;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.ProceedWithDefaultAdviserParameters;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import javax.validation.constraints.NotNull;

public class ProceedWithDefaultValueAdviser implements Adviser {
  public static final AdviserType ADVISER_TYPE =
      AdviserType.newBuilder().setType(OrchestrationAdviserTypes.PROCEED_WITH_DEFAULT.name()).build();
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public AdviserResponse onAdviseEvent(AdvisingEvent advisingEvent) {
    return AdviserResponse.newBuilder()
        .setProceedWithDefaultAdvise(ProceedWithDefaultAdvise.newBuilder().build())
        .setType(AdviseType.PROCEED_WITH_DEFAULT)
        .build();
  }

  @Override
  public boolean canAdvise(AdvisingEvent advisingEvent) {
    return advisingEvent.getFromStatus().equals(Status.INPUT_WAITING);
  }

  @NotNull
  private ProceedWithDefaultAdviserParameters extractParameters(AdvisingEvent advisingEvent) {
    return (ProceedWithDefaultAdviserParameters) Preconditions.checkNotNull(
        kryoSerializer.asObject(advisingEvent.getAdviserParameters()));
  }
}
