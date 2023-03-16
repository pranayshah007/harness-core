/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.mutation.service.input;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.pcf.model.CfCliVersion;
import io.harness.utils.RequestField;

import software.wings.graphql.schema.mutation.QLMutationInput;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLUpdateServiceMetadataInputKeys")
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLUpdateServiceMetadataInput implements QLMutationInput {
  String clientMutationId;
  List<String> applicationId;
  RequestField<List<String>> excludeServices;
  CfCliVersion cfCliVersion;
}
