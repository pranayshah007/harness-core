/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.beans;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofMinutes;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@FieldNameConstants(innerTypeName = "DelegateConnectionKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateConnections", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class DelegateConnection implements PersistentEntity, UuidAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("index2")
                 .field(DelegateConnectionKeys.accountId)
                 .field(DelegateConnectionKeys.delegateId)
                 .field(DelegateConnectionKeys.version)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("lastHeartbeatIndex")
                 .field(DelegateConnectionKeys.disconnected)
                 .sortField(DelegateConnectionKeys.lastHeartbeat)
                 .build())
        .build();
  }

  public static final Duration TTL = ofDays(15);
  public static final Duration EXPIRY_TIME = ofMinutes(5);

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @NotEmpty private String accountId;
  @NotEmpty private String delegateId;
  private String version;
  private String location;
  private long lastHeartbeat;
  private long lastGrpcHeartbeat;
  private boolean disconnected;

  @JsonIgnore @SchemaIgnore @FdTtlIndex private Date validUntil;
}
