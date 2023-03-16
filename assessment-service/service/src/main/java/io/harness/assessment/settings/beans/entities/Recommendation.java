/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.beans.entities;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "RecommendationEntityKeys")
@StoreIn(DbAliases.ASSESSMENT)
@Entity(value = "recommendations", noClassnameStored = true)
@Document("recommendations")
@Persistent
@OwnedBy(HarnessTeam.SEI)
public class Recommendation implements PersistentEntity, UpdatedAtAware {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @FdIndex @NotEmpty @EntityIdentifier String recommendationId;
  String assessmentId;
  String version;
  String organizationId;
  List<RecommendationEntry> recommendations;
  @LastModifiedDate long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_recommendation_id")
                 .unique(true)
                 .field(RecommendationEntityKeys.recommendationId)
                 .field(RecommendationEntityKeys.version)
                 .build())
        .build();
  }
}
