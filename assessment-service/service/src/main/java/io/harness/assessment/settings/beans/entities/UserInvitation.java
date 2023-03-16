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
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "UserInvitationEntityKeys")
@StoreIn(DbAliases.ASSESSMENT)
@Entity(value = "userInvitations", noClassnameStored = true)
@Document("userInvitations")
@Persistent
@OwnedBy(HarnessTeam.SEI)
public class UserInvitation implements PersistentEntity, CreatedAtAware, UpdatedAtAware {
  @Id @org.mongodb.morphia.annotations.Id String id;
  String userId;
  String assessmentId;
  String generatedCode;
  String invitedBy;
  @CreatedDate long createdAt;
  @LastModifiedDate long lastUpdatedAt;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_assessmentId_userId_idx")
                 .unique(true)
                 .field(UserInvitationEntityKeys.assessmentId)
                 .field(UserInvitationEntityKeys.userId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("unique_generatedCode_idx")
                 .unique(true)
                 .field(UserInvitationEntityKeys.generatedCode)
                 .build())
        .build();
  }
}
