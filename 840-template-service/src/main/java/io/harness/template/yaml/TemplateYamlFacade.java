/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public final class TemplateYamlFacade {
  private final boolean minimizeQuotes = false;

  public String writeYamlString(Object value) {
    if (minimizeQuotes) {
      return TemplateYamlUtils.writeYamlString(value);
    }
    return YamlPipelineUtils.writeYamlString(value);
  }
}
