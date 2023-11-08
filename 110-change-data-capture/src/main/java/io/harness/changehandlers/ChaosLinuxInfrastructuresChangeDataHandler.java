/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.entities.subscriptions.ChaosLinuxInfrastructures.ChaosLinuxInfrastructuresKeys;

import java.util.List;

public class ChaosLinuxInfrastructuresChangeDataHandler
    extends BasicEntityToColumnsChangeDataHandler<ChaosLinuxInfrastructuresKeys> {
  @Override
  public List<String> getPrimaryKeys() {
    return List.of(ChaosLinuxInfrastructuresKeys.id.toString());
  }

  @Override
  protected ChaosLinuxInfrastructuresKeys[] getKeysToMap() {
    return ChaosLinuxInfrastructuresKeys.values();
  }

  @Override
  protected List<ChaosLinuxInfrastructuresKeys> getExcludedKeys() {
    return List.of(ChaosLinuxInfrastructuresKeys.id, ChaosLinuxInfrastructuresKeys.tags);
  }
}
