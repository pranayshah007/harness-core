/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.entities;

import io.harness.ModuleType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(HarnessTeam.GTM)
@Data
@Builder
@FieldNameConstants(innerTypeName = "SubscriptionDetailKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "subscriptionDetails", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("subscriptionDetails")
@Persistent
public class SubscriptionDetail implements PersistentEntity, NGAccountAccess {
  public static final String INCOMPLETE = "incomplete";

  @Id @dev.morphia.annotations.Id protected String id;
  @Trimmed @NotEmpty protected String accountIdentifier;
  @NotEmpty protected ModuleType moduleType;
  @Trimmed @NotEmpty protected String subscriptionId;
  @Trimmed @NotEmpty protected String customerId;
  protected String status;
  protected String latestInvoice;
  protected Long cancelAt;
  protected Long canceledAt;
  @CreatedBy protected EmbeddedUser createdBy;
  @LastModifiedBy protected EmbeddedUser lastUpdatedBy;
  @CreatedDate protected Long createdAt;
  @LastModifiedDate protected Long lastUpdatedAt;

  //  public static List<MongoIndex> mongoIndexes() {
  //    return ImmutableList.<MongoIndex>builder()
  //        .add(CompoundMongoIndex.builder()
  //                 .name("accountIdentifier_and_moduletype_subscriptionDetail_query_index")
  //                 .fields(Arrays.asList(SubscriptionDetailKeys.accountIdentifier, SubscriptionDetailKeys.moduleType))
  //                 .build())
  //        .add(CompoundMongoIndex.builder()
  //                 .name("subscriptionId_subscriptionDetail_query_index")
  //                 .fields(Arrays.asList(SubscriptionDetailKeys.subscriptionId))
  //                 .build())
  //        .build();
  //  }

  public boolean isIncomplete() {
    return INCOMPLETE.equalsIgnoreCase(status);
  }
  public boolean isActive() {
    return "active".equalsIgnoreCase(status);
  }
}
