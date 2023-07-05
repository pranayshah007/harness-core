/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.runners.itfc;

import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.service.handlermapping.context.Context;

public interface Runner {
  void init(String infraId, InputData infra, Context context);
  void execute(String infraId, InputData tasks, Context context);
  void cleanup(String infraId, Context context);
}
