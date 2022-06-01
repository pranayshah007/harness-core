/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.plugin.compatible;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.List;

@OwnedBy(CI)
public interface PluginCompatibleStep extends CIStepInfo {
  // Common for all plugin compatible step types
  ParameterField<String> getConnectorRef();
  ContainerResource getResources();
  ParameterField<Integer> getRunAsUser();
  default ParameterField<List<String>> getBaseImageConnectorRefs() {
    return new ParameterField<>();
  }
}
