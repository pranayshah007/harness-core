/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.entity;

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
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.Rule;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Jacksonized
@FieldNameConstants(innerTypeName = "CheckKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "checks", noClassnameStored = true)
@Document("checks")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class CheckEntity implements PersistentEntity, CreatedByAware, UpdatedByAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(CheckKeys.accountIdentifier)
                 .field(CheckKeys.identifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_custom_deleted")
                 .field(CheckKeys.accountIdentifier)
                 .field(CheckKeys.isCustom)
                 .field(CheckKeys.isDeleted)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private String description;

  private CheckDetails.RuleStrategyEnum ruleStrategy;
  private List<Rule> rules;

  // ALL OF -> github.isBranchProtected=true && catalog.spec.owner!=null
  // ANY OF -> github.isBranchProtected=true || catalog.spec.owner!=null
  private String expression;

  /*
    pre-populated -> harnessManaged=true, isCustom=false
    custom check from dropdown -> harnessManaged=true, isCustom=true
    custom check from expression (not supported now) -> harnessManaged=false, isCustom=true
  * */
  @Builder.Default
  private boolean harnessManaged = true; // dropdown (default or custom) - we know data source and data point
  private boolean isCustom; // for the purpose of UI

  private List<String> tags;
  private CheckDetails.DefaultBehaviourEnum defaultBehaviour;
  private String failMessage;
  private boolean isDeleted;
  private long deletedAt;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @Builder.Default @CreatedDate private long createdAt = System.currentTimeMillis();
  @LastModifiedDate private long lastUpdatedAt;
}
