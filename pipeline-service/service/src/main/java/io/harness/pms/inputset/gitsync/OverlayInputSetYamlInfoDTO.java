/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.gitsync;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class OverlayInputSetYamlInfoDTO {
  @EntityName String name;
  @EntityIdentifier String identifier;

  String description;
  Map<String, String> tags;

  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;

  List<String> inputSetReferences;
}
