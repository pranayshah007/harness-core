/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.entity;

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
@FieldNameConstants(innerTypeName = "DataPointKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "dataPoints", noClassnameStored = true)
@Document("dataPoints")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class DataPointEntity implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(DataPointKeys.accountIdentifier)
                 .field(DataPointKeys.identifier)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String identifier;
  private String name;
  private Type type;
  private String expression;
  private boolean isConditional;

  // {DS}.{DP}.{INV}{OP}{VALUE}
  //       isConditional = true             isConditional = false
  // github.isBranchProtected.main=true && catalog.spec.owner=true
  // DS -> github, DP -> isBranchProtected, INV -> main, OP -> =, value = true

  // github.isBranchProtected.main=true && github.isBranchProtected.develop=true
  /*

  isBranchProtected -> parser


    {
      github: {
        isBranchProtected: {
          main: true
        }
      }
    }
    {
      github: {
        isBranchProtected: {
          develop: true
        }
      }
    }

    MERGE -> Github DS response
    {
      github: {
        isBranchProtected: {
          main: true
          develop: true
        }
      }
    }
  * */

  // isConditional = false
  // {DS}.{DP}{OP}{VALUE}
  // github.readme.size>5000

  private String dataSourceLocationIdentifier;

  public enum Type { NUMBER, BOOLEAN, STRING }
}
