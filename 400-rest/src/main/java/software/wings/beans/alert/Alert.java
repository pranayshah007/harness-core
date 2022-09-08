/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.alert;

import io.harness.alert.AlertData;
import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.alerts.AlertCategory;
import software.wings.alerts.AlertSeverity;
import software.wings.alerts.AlertStatus;
import software.wings.beans.alert.AlertReconciliation.AlertReconciliationKeys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@FieldNameConstants(innerTypeName = "AlertKeys")
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "alerts")
@HarnessEntity(exportable = false)
@TargetModule(HarnessModule._955_ALERT_BEANS)
public class Alert
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, PersistentRegularIterable, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountAppTypeStatusIdx")
                 .field(AlertKeys.accountId)
                 .field(AlertKeys.appId)
                 .field(AlertKeys.type)
                 .field(AlertKeys.status)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountTypeStatusIdx")
                 .field(AlertKeys.accountId)
                 .field(AlertKeys.type)
                 .field(AlertKeys.status)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("createdAtTypeIndex")
                 .field(AlertKeys.type)
                 .ascSortField(AlertKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("reconciliationIterator")
                 .field(AlertKeys.status)
                 .field(AlertKeys.alertReconciliation_needed)
                 .field(AlertKeys.alertReconciliation_nextIteration)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private long createdAt;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  private String accountId;
  private AlertType type;
  private AlertStatus status;
  private String title;
  private String resolutionTitle;
  private AlertCategory category;
  private AlertSeverity severity;
  private AlertData alertData;
  private AlertReconciliation alertReconciliation;
  private long closedAt;
  private int triggerCount;
  private long lastTriggeredAt;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  @Default
  private Date validUntil = Date.from(OffsetDateTime.now().plusDays(14).toInstant());

  @FdIndex private Long cvCleanUpIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (AlertKeys.cvCleanUpIteration.equals(fieldName)) {
      this.cvCleanUpIteration = nextIteration;
      return;
    }
    if (AlertKeys.alertReconciliation_nextIteration.equals(fieldName)) {
      this.alertReconciliation.setNextIteration(nextIteration);
      return;
    }
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (AlertKeys.cvCleanUpIteration.equals(fieldName)) {
      return this.cvCleanUpIteration;
    }
    if (AlertKeys.alertReconciliation_nextIteration.equals(fieldName)) {
      return this.alertReconciliation.getNextIteration();
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @UtilityClass
  public static final class AlertKeys {
    public static final String alertReconciliation_needed =
        AlertKeys.alertReconciliation + "." + AlertReconciliationKeys.needed;
    public static final String alertReconciliation_nextIteration =
        alertReconciliation + "." + AlertReconciliationKeys.nextIteration;
  }
}
