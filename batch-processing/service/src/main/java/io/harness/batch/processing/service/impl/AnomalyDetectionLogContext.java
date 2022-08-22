/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import io.harness.logging.AutoLogContext;

public class AnomalyDetectionLogContext extends AutoLogContext {
  public static final String ID = "AnomalyId";

  public AnomalyDetectionLogContext(String id, AutoLogContext.OverrideBehavior behavior) {
    super(ID, id, behavior);
  }
}
