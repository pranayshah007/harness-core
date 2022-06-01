/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.connector;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.QLUser;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@Scope(PermissionAttribute.ResourceType.SETTING)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLOciHelmRepoConnector implements QLConnector {
  private String id;
  private String name;
  private Long createdAt;
  private QLUser createdBy;

  private String URL;
  private String userName;
  private String passwordSecretId;

  public static class QLOciHelmRepoConnectorBuilder implements QLConnectorBuilder {}
}
