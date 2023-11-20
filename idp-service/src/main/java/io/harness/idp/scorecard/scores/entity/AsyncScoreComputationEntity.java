/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AsyncScoreComputationKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "asyncScoreComputation", noClassnameStored = true)
@Document("asyncScoreComputation")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class AsyncScoreComputationEntity implements PersistentEntity, CreatedByAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountIdentifier_entityIdentifier_scorecardIdentifier")
                 .unique(true)
                 .field(AsyncScoreComputationKeys.accountIdentifier)
                 .field(AsyncScoreComputationKeys.entityIdentifier)
                 .field(AsyncScoreComputationKeys.scorecardIdentifier)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String entityIdentifier;
  private String scorecardIdentifier;
  private long startTime;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @Builder.Default @CreatedDate private long createdAt = System.currentTimeMillis();
}
