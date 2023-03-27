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
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "OrganizationEvaluationEntityKeys")
@StoreIn(DbAliases.ASSESSMENT)
@Entity(value = "organizationEvaluations", noClassnameStored = true)
@Document("organizationEvaluations")
@Persistent
@OwnedBy(HarnessTeam.SEI)
public class OrganizationEvaluation implements PersistentEntity, UpdatedAtAware {
  @Id @org.mongodb.morphia.annotations.Id String id;
  String organizationId;
  String assessmentId;
  Long version;
  List<Score> scores;
  Long numOfResponses;
  @LastModifiedDate long lastUpdatedAt;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_assessment_id")
                 .unique(true)
                 .field(OrganizationEvaluation.OrganizationEvaluationEntityKeys.organizationId)
                 .field(OrganizationEvaluation.OrganizationEvaluationEntityKeys.assessmentId)
                 .field(OrganizationEvaluation.OrganizationEvaluationEntityKeys.version)
                 .build())
        .build();
  }
}
