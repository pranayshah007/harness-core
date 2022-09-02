/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.marketplace.gcp;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.UpdatedAtAccess;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.SchemaIgnore;
import java.time.Instant;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(PL)
@FieldNameConstants(innerTypeName = "GCPBillingJobEntityKeys")
@Getter
@ToString
@EqualsAndHashCode
@StoreIn(DbAliases.HARNESS)
@Entity(value = "gcpBillingJobEntity")
@HarnessEntity(exportable = true)
public class GCPBillingJobEntity implements PersistentRegularIterable, CreatedAtAccess, UpdatedAtAccess, AccountAccess {
  @Id private String uuid;
  @FdIndex private String accountId;
  private String gcpAccountId;
  @Setter @FdIndex private Long nextIteration;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore @NotNull private long createdAt;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore @NotNull private long lastUpdatedAt;

  public GCPBillingJobEntity(String accountId, String gcpAccountId, Long nextIteration) {
    long currentMillis = Instant.now().toEpochMilli();
    this.uuid = accountId;
    this.accountId = accountId;
    this.gcpAccountId = gcpAccountId;
    this.nextIteration = nextIteration;
    this.createdAt = currentMillis;
    this.lastUpdatedAt = currentMillis;
  }

  public GCPBillingJobEntity(String accountId, Long nextIteration) {
    this(accountId, null, nextIteration);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }

  @Override
  public String getUuid() {
    return accountId;
  }
}
