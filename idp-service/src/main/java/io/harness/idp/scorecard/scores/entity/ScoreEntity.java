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
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.spec.server.idp.v1.model.CheckStatus;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
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
@FieldNameConstants(innerTypeName = "ScoreKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "scores", noClassnameStored = true)
@Document("scores")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class ScoreEntity implements PersistentEntity, CreatedByAware, CreatedAtAware {
  public static final long TTL_MONTHS = 6;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_entityIdentifier_scorecardIdentifier_lastComputedTimestamp")
                 .unique(true)
                 .field(ScoreKeys.accountIdentifier)
                 .field(ScoreKeys.entityIdentifier)
                 .field(ScoreKeys.scorecardIdentifier)
                 .field(ScoreKeys.lastComputedTimestamp)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String entityIdentifier;
  private String scorecardIdentifier;
  private long lastComputedTimestamp;
  private int score;
  private List<CheckStatus> checkStatus;
  @Builder.Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant());
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @Builder.Default @CreatedDate private long createdAt = System.currentTimeMillis();
}
