/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.expressions.usages;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@StoreIn(DbAliases.PMS)
@FieldNameConstants(innerTypeName = "ExpressionUsagesEntityKeys")
@Entity(value = "expressionUsage")
@Document("expressionUsage")
@TypeAlias("expressionUsage")
@OwnedBy(HarnessTeam.PIPELINE)
public class ExpressionUsagesEntity implements PersistentEntity {
  @NonFinal @Id @dev.morphia.annotations.Id String uuid;
  @NotEmpty private String accountIdentifier;
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotEmpty private String pipelineIdentifier;
  private Map<ExpressionCategory, Set<ExpressionMetadata>> expressionsMap;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_orgId_projectId_pipelineId")
                 .field(ExpressionUsagesEntityKeys.accountIdentifier)
                 .field(ExpressionUsagesEntityKeys.orgIdentifier)
                 .field(ExpressionUsagesEntityKeys.projectIdentifier)
                 .field(ExpressionUsagesEntityKeys.pipelineIdentifier)
                 .unique(true)
                 .build())
        .build();
  }
}
