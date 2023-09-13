/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.beans.provenance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import lombok.Value;

@Value
@OwnedBy(HarnessTeam.SSCA)
public class BuildMetadata {
  private ParameterField<String> image;
  private ParameterField<Map<String, String>> buildArgs;
  private ParameterField<String> context;
  private ParameterField<String> dockerFile;
  private ParameterField<Map<String, String>> labels;
}
