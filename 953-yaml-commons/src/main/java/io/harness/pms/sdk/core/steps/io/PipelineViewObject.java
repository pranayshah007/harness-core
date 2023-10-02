/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.steps.io;

import java.util.LinkedList;
import java.util.List;

public interface PipelineViewObject {
  String DEFAULT = "default";

  // This is a list of keys which needs to be excluded from stepParameter to view for customer, if nested keys ->
  // separate keys by dot, example spec.output to remove only output inside spec
  default List<String> excludeKeysFromStepInputs() {
    return new LinkedList<>();
  }
}
