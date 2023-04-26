/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.registrars;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.advisers.CDStepsRollbackModeAdviser;
import io.harness.cdng.advisers.RollbackCustomAdviser;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.sdk.core.adviser.Adviser;
import io.harness.serializer.PipelineServiceUtilAdviserRegistrar;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class CDServiceAdviserRegistrar {
  public Map<AdviserType, Class<? extends Adviser>> getEngineAdvisers() {
    Map<AdviserType, Class<? extends Adviser>> advisersMap =
        new HashMap<>(PipelineServiceUtilAdviserRegistrar.getEngineAdvisers());
    advisersMap.put(RollbackCustomAdviser.ADVISER_TYPE, RollbackCustomAdviser.class);
    advisersMap.put(CDStepsRollbackModeAdviser.ADVISER_TYPE, CDStepsRollbackModeAdviser.class);
    return advisersMap;
  }
}
