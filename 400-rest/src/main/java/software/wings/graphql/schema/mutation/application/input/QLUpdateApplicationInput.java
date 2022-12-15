/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.schema.mutation.application.input;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.utils.RequestField;

import software.wings.graphql.schema.mutation.QLMutationInput;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLUpdateApplicationInputKeys")
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLUpdateApplicationInput implements QLMutationInput {
  private String clientMutationId;
  private String applicationId;
  private RequestField<String> name;
  private RequestField<String> description;
  private Boolean isManualTriggerAuthorized;
  private Boolean areWebHookSecretsMandated;
  private Boolean disableTriggers;
}
