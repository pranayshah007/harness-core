/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.changestreamsframework.ChangeEvent;
import io.harness.entities.subscriptions.ChaosExperiments.ChaosExperimentsKeys;

import com.mongodb.DBObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChaosExperimentsChangeDataHandler extends BasicEntityToColumnsChangeDataHandler<ChaosExperimentsKeys> {
  @Override
  public List<String> getPrimaryKeys() {
    return List.of(ChaosExperimentsKeys.id.toString());
  }

  @Override
  protected ChaosExperimentsKeys[] getKeysToMap() {
    return ChaosExperimentsKeys.values();
  }

  @Override
  protected List<ChaosExperimentsKeys> getExcludedKeys() {
    return List.of(ChaosExperimentsKeys.id, ChaosExperimentsKeys.tags);
  }
}
