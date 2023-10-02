/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.entity;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.AgreementType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldNameConstants(innerTypeName = "EulaKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "eula", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("eula")
@Persistent
@TypeAlias("eula")
public class Eula implements PersistentEntity {
  @Id @dev.morphia.annotations.Id String id;
  @NotEmpty String accountIdentifier;
  Set<AgreementType> signedAgreements;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_unique_idx")
                 .field(EulaKeys.accountIdentifier)
                 .unique(true)
                 .build())
        .build();
  }
}