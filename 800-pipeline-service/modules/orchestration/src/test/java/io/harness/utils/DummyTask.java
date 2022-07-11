/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.tasks.Task;

import lombok.Value;

/**
 * The type Dummy task.
 * This is only to provide a Dummy Binding to Guice else it complains while running tests
 */
@Value
public class DummyTask implements Task {
  String uuid;
  String waitId;
}
