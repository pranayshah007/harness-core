/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup.entities;

import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "signupVerificationTokensKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "signupVerificationTokens", noClassnameStored = true)
@Document("signupVerificationTokens")
@TypeAlias("signupVerificationTokens")
public class SignupVerificationToken {
  @Id @org.mongodb.morphia.annotations.Id String id;
  @NotEmpty String token;
  @NotEmpty String email;
  String accountIdentifier;
  String userId;
  String appId;
  Long validUntil;
  @CreatedDate long createdAt;
  @LastModifiedDate long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("token_signupVerificationToken_query_index")
                 .fields(Arrays.asList(signupVerificationTokensKeys.token))
                 .build())
        .build();
  }
}
