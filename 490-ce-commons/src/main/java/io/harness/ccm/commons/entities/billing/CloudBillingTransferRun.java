/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "cloudBillingTransferRuns", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "CloudBillingTransferRunKeys")
@OwnedBy(CE)
public final class CloudBillingTransferRun implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware {
  @Id String uuid;
  private String accountId;
  private String organizationUuid;
  private String billingDataPipelineRecordId;
  private String transferRunResourceName;
  private TransferJobRunState state;
  long lastUpdatedAt;
}
