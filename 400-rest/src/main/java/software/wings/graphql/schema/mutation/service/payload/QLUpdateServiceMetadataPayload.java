/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.graphql.schema.mutation.service.payload;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.pcf.model.CfCliVersion;

import software.wings.graphql.schema.mutation.QLMutationPayload;
import software.wings.graphql.schema.type.QLApplication;
import software.wings.graphql.schema.type.QLService;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Scope(PermissionAttribute.ResourceType.APPLICATION)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLUpdateServiceMetadataPayload implements QLMutationPayload {
  String clientMutationId;
  List<QLApplication> application;
  CfCliVersion cfCliVersion;
  List<QLService> updatedService;
}
