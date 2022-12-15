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
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import java.time.Instant;
import java.util.List;
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
@Entity(value = "billingDataPipelineRecord", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "BillingDataPipelineRecordKeys")
@OwnedBy(CE)
public final class BillingDataPipelineRecord implements PersistentEntity, UuidAware, CreatedAtAware, AccountAccess {
  @Id private String uuid;
  long createdAt;

  private String accountId;
  private String settingId;
  private String accountName;
  private String cloudProvider;

  private String dataSetId;
  private String dataTransferJobName;
  private String transferJobResourceName;
  private String preAggregatedScheduledQueryName;
  private String preAggregatedScheduleQueryResourceName;

  private String gcpBqProjectId;
  private String gcpBqDatasetId;
  private String awsMasterAccountId;
  private String awsFallbackTableScheduledQueryName;

  private String dataTransferJobStatus;
  private String preAggregatedScheduledQueryStatus;
  private String awsFallbackTableScheduledQueryStatus;
  private Instant lastSuccessfulS3Sync;
  private Instant lastSuccessfulStorageSync;

  private List<String> awsLinkedAccountsToExclude;
  private List<String> awsLinkedAccountsToInclude;
}
