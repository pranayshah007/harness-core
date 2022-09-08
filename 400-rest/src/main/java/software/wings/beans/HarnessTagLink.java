/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static software.wings.beans.HarnessTagType.USER;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonView;
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

@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@JsonInclude(Include.NON_NULL)
@FieldNameConstants(innerTypeName = "HarnessTagLinkKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "tagLinks", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class HarnessTagLink implements PersistentEntity, UuidAware, UpdatedAtAware, UpdatedByAware, CreatedAtAware,
                                       CreatedByAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("entityTagIdx")
                 .unique(true)
                 .field(HarnessTagLinkKeys.accountId)
                 .field(HarnessTagLinkKeys.entityId)
                 .field(HarnessTagLinkKeys.key)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("tagValueIdx")
                 .field(HarnessTagLinkKeys.accountId)
                 .field(HarnessTagLinkKeys.key)
                 .field(HarnessTagLinkKeys.value)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotEmpty private String accountId;
  @NotEmpty private String appId;
  @NotEmpty private String key;
  private String value;
  @NotNull private EntityType entityType;
  @NotEmpty private String entityId;
  @Builder.Default private HarnessTagType tagType = USER;

  private transient String appName;
  private transient String entityName;

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private EmbeddedUser createdBy;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private long createdAt;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @JsonView(JsonViews.Internal.class) @SchemaIgnore @NotNull private long lastUpdatedAt;
}
