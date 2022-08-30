/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdSparseIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAccess;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "TestEntityKeys")
@StoreIn(DbAliases.TEST)
@Entity(value = "!!!testIndexes", noClassnameStored = true)
public class TestIndexEntity implements PersistentEntity, UuidAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("index").field(TestEntityKeys.name).field(TestEntityKeys.test).build())
        .add(CompoundMongoIndex.builder()
                 .name("sparse_index")
                 .sparse(true)
                 .field(TestEntityKeys.name)
                 .field(TestEntityKeys.sparseTest)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String name;
  @FdIndex private String test;
  @FdUniqueIndex private String uniqueTest;
  @FdSparseIndex private String sparseTest;
  @FdTtlIndex(11) private String ttlTest;
}
