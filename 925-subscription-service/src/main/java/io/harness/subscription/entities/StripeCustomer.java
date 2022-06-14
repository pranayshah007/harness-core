/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.subscription.entities;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
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
@FieldNameConstants(innerTypeName = "StripeCustomerKeys")
@Entity(value = "stripeCustomers", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("stripeCustomers")
@Persistent
public class StripeCustomer implements PersistentEntity, NGAccountAccess {
  @Id @org.mongodb.morphia.annotations.Id protected String id;
  @Trimmed @NotEmpty protected String accountIdentifier;
  @Trimmed @NotEmpty protected String customerId;
  @NotEmpty protected String billingEmail;
  @NotEmpty protected String companyName;
  @CreatedBy protected EmbeddedUser createdBy;
  @LastModifiedBy protected EmbeddedUser lastUpdatedBy;
  @CreatedDate protected Long createdAt;
  @LastModifiedDate protected Long lastUpdatedAt;

  //  public static List<MongoIndex> mongoIndexes() {
  //    return ImmutableList.<MongoIndex>builder()
  //        .add(CompoundMongoIndex.builder()
  //                 .name("accountIdentifier_customerId_stripeCustomer_query_index")
  //                 .fields(Arrays.asList(StripeCustomerKeys.accountIdentifier, StripeCustomerKeys.customerId))
  //                 .build())
  //        .build();
  //  }
}
