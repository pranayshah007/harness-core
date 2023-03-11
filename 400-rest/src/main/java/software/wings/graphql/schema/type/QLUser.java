/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLUserKeys")
@Scope(ResourceType.USER)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLUser implements QLObject {
  private String id;
  private String name;
  private String email;
  private Boolean isEmailVerified;
  private Boolean isTwoFactorAuthenticationEnabled;
  private Boolean isUserLocked;
  private Boolean isPasswordExpired;
  private Boolean isImportedFromIdentityProvider;
  private String externalUserId;
  private Boolean isDisabled;
}
