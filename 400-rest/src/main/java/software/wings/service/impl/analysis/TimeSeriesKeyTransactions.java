/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@org.mongodb.morphia.annotations.Entity(value = "timeSeriesKeyTransactions", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "TimeSeriesKeyTransactionsKeys")
public class TimeSeriesKeyTransactions implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware,
                                                  UpdatedAtAware, UpdatedByAware, AccountAccess {
  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;
  private EmbeddedUser createdBy;
  private EmbeddedUser lastUpdatedBy;

  @FdIndex private String cvConfigId;
  @FdIndex private String serviceId;
  @FdIndex private String accountId;
  private Set<String> keyTransactions;
}
