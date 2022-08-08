/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.security.encryption.AdditionalMetadata;

import software.wings.security.ScopedEntity;
import software.wings.security.UsageRestrictions;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "HarnessSecretKeys")
public class HarnessSecret implements ScopedEntity {
  @NotEmpty String name;
  String kmsId;
  UsageRestrictions usageRestrictions;
  boolean scopedToAccount;
  boolean hideFromListing;
  boolean inheritScopesFromSM;
  private AdditionalMetadata additionalMetadata;
  Map<String, String> runtimeParameters;
}
