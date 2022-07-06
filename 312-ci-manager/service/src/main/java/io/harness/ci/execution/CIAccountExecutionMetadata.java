/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.pms.plan.execution.AccountExecutionInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(CI)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CIAccountExecutionMetadataKeys")
@Entity(value = "ciAccountExecutionMetadata", noClassnameStored = true)
@Document("ciAccountExecutionMetadata")
@TypeAlias("ciAccountExecutionMetadata")
@HarnessEntity(exportable = true)
@StoreIn(DbAliases.NG_MANAGER)
public class CIAccountExecutionMetadata {
  @Wither @Id @org.mongodb.morphia.annotations.Id String uuid;
  String accountId;
  Long executionCount;
  AccountExecutionInfo accountExecutionInfo;
}
