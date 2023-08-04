/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "ScorecardKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "Scorecards", noClassnameStored = true)
@Document("Scorecards")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class ScorecardEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(ScorecardKeys.accountIdentifier)
                 .field(ScorecardKeys.identifier)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private String description;
  private List<Filter> filter;
  private WeightageStrategy weightageStrategy;
  private List<Check> checks;
  private boolean published;
  private boolean isDeleted;
  private long deletedAt;

  @Data
  @Builder
  public static class Check {
    private String identifier;
    private double weightage;
  }

  @Data
  @Builder
  public static class Filter {
    private String component;
    private String kind;
    private List<String> owner;
    private List<String> tags;
    private List<String> lifecycle;
  }

  public enum WeightageStrategy { EQUAL_WEIGHTS, CUSTOM }
}
