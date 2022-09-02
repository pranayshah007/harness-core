/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.limits.lib.Limit;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Getter
@EqualsAndHashCode(exclude = "id", callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "allowedLimits", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "ConfiguredLimitKeys")
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class ConfiguredLimit<T extends Limit> implements PersistentEntity, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_key_idx")
                 .unique(true)
                 .field(ConfiguredLimitKeys.key)
                 .field(ConfiguredLimitKeys.accountId)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private ObjectId id;

  private String accountId;
  private String key;
  private T limit;

  public ConfiguredLimit(String accountId, T limit, ActionType actionType) {
    this.accountId = accountId;
    this.key = actionType.toString();
    this.limit = limit;
  }

  public T getLimit() {
    return limit;
  }

  // morphia expects an no-args constructor
  private ConfiguredLimit() {}
}
