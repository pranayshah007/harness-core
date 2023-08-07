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
@FieldNameConstants(innerTypeName = "CheckKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "checks", noClassnameStored = true)
@Document("checks")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class CheckEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(CheckKeys.accountIdentifier)
                 .field(CheckKeys.identifier)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private String description;

  private RuleStrategy ruleStrategy;
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
  private DefaultBehaviour defaultBehaviour;
  private String failMessage;

  @Data
  @Builder
  public static class Rule {
    private String dataSourceIdentifier;
    private String dataPointIdentifier;
    private String operator;
    private String value;
  }

  public enum RuleStrategy { ALL_OF, ANY_OF }
  public enum DefaultBehaviour { PASS, FAIL }
}
