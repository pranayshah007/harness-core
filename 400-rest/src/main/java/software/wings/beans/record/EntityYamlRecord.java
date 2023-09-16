/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.record;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "entityYamlRecord", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "EntityYamlRecordKeys")
public class EntityYamlRecord implements PersistentEntity, UuidAccess, CreatedAtAccess, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("index_1")
                 .field(EntityYamlRecordKeys.accountId)
                 .field(EntityYamlRecordKeys.entityId)
                 .field(EntityYamlRecordKeys.entityType)
                 .descSortField(EntityYamlRecordKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("index_2")
                 .field(EntityYamlRecordKeys.accountId)
                 .field(EntityYamlRecordKeys.entityType)
                 .descSortField(EntityYamlRecordKeys.createdAt)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String accountId;
  private long createdAt;
  private String entityId;
  private String entityType;
  private String yamlPath;
  private String yamlSha;
  private String yamlContent;
}
