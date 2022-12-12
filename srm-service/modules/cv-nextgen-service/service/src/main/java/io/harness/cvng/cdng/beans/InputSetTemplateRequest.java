/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import lombok.Builder;
import lombok.Value;
@Value
@Builder
public class InputSetTemplateRequest {
  String pipelineYaml;
  String templateYaml; // We don't need this in the current implementation but keeping it as part of api in case future
                       // implementation changes.
}
