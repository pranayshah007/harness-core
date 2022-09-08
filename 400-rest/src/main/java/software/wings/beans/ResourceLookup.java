/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@FieldNameConstants(innerTypeName = "ResourceLookupKeys")

@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "resourceLookup", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class ResourceLookup implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("resourceIndex_1")
                 .field(ResourceLookupKeys.accountId)
                 .field(ResourceLookupKeys.resourceType)
                 .field(ResourceLookupKeys.appId)
                 .field(ResourceLookupKeys.resourceName)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("resourceIndex_3")
                 .field(ResourceLookupKeys.accountId)
                 .field(ResourceLookupKeys.resourceName)
                 .field(ResourceLookupKeys.resourceType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("tagsNameResourceLookupIndex")
                 .field(ResourceLookupKeys.accountId)
                 .field("tags.name")
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("resourceIdResourceLookupIndex")
                 .field(ResourceLookupKeys.accountId)
                 .field(ResourceLookupKeys.resourceId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_ResourceLookup_Index")
                 .field(ResourceLookupKeys.accountId)
                 .descSortField(ResourceLookupKeys.createdAt)
                 .build())
        .build();
  }
  public static final String GLOBAL_APP_ID = "__GLOBAL_APP_ID__";

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @NotEmpty private String accountId;
  @NotEmpty private String appId;
  @FdIndex @NotEmpty private String resourceId;
  @NotEmpty private String resourceType;
  private String resourceName;
  private List<NameValuePair> tags;
  @FdIndex private long createdAt;
  private long lastUpdatedAt;
}
