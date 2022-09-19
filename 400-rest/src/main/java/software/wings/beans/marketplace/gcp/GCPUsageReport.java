/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.marketplace.gcp;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAccess;
import io.harness.persistence.UuidAccess;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(PL)
@Value
@FieldNameConstants(innerTypeName = "GCPUsageReportKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gcpUsageReport", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class GCPUsageReport implements PersistentEntity, UuidAccess, CreatedAtAccess, UpdatedAtAccess, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("accountId_startTimestamp_unique_idx")
                 .field(GCPUsageReportKeys.accountId)
                 .field(GCPUsageReportKeys.startTimestamp)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NonFinal private String accountId;
  @NonFinal private String consumerId;
  @NonFinal private String operationId;
  @NonFinal private String entitlementName;
  @NonFinal private Instant startTimestamp;
  @NonFinal private Instant endTimestamp;
  @NonFinal private long instanceUsage;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private long createdAt;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore @NotNull private long lastUpdatedAt;

  public GCPUsageReport(String accountId, String consumerId, String operationId, String entitlementName,
      Instant usageStartTime, Instant usageEndTime, long instanceUsage) {
    long currentMillis = Instant.now().toEpochMilli();
    this.uuid = String.format("%s-%s", accountId, usageStartTime.toEpochMilli());
    this.accountId = accountId;
    this.consumerId = consumerId;
    this.operationId = operationId;
    this.entitlementName = entitlementName;
    this.startTimestamp = usageStartTime;
    this.endTimestamp = usageEndTime;
    this.instanceUsage = instanceUsage;
    this.createdAt = currentMillis;
    this.lastUpdatedAt = currentMillis;
  }
}
