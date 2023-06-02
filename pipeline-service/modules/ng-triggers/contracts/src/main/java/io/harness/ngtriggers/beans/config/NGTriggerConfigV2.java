/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.config;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.validator.NGRegexValidatorConstants;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(PIPELINE)
public class NGTriggerConfigV2 implements NGTriggerInterface {
  @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  String description;
  String orgIdentifier;
  String projectIdentifier;
  String pipelineIdentifier;
  Map<String, String> tags;
  String inputYaml;
  NGTriggerSourceV2 source;
  String pipelineBranchName;
  List<String> inputSetRefs;
  @Builder.Default Boolean enabled = Boolean.TRUE;
  String encryptedWebhookSecretIdentifier;
  List<String> stagesToExecute;
}
