/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.artifactSource;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDC)
@Value
@SuperBuilder
@FieldNameConstants(innerTypeName = "QLAzureArtifactsArtifactSourceKeys")
@Scope(PermissionAttribute.ResourceType.SERVICE)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLAzureArtifactsArtifactSource extends QLArtifactSourceBase {
  String packageType;
  String project;
  String scope;
  String feedName;
  String packageName;
  String azureConnectorId;
}
