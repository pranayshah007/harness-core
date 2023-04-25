/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.models;

import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "BlockedACLEntityKeys")
@StoreIn(ACCESS_CONTROL)
@Entity(value = "blockedAccount", noClassnameStored = true)
@Document("blockedAccount")
@TypeAlias("blockedAccount")
public class BlockedAccount {
  String accountIdentifier;
}
