/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.plan.execution.AccountExecutionInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CDAccountExecutionMetadataKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "cdAccountExecutionMetadata", noClassnameStored = true)
@Document("cdAccountExecutionMetadata")
@TypeAlias("cdAccountExecutionMetadata")
@HarnessEntity(exportable = true)
public class CDAccountExecutionMetadata implements PersistentEntity {
  @Wither @Id @dev.morphia.annotations.Id String uuid;
  String accountId;
  Long executionCount;
  AccountExecutionInfo accountExecutionInfo;
}
