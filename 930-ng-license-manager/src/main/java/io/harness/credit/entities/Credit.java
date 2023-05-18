/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.credit.entities;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.CreditType;
import io.harness.ModuleType;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.utils.CreditStatus;
import io.harness.data.validator.Trimmed;
import io.harness.ng.DbAliases;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(GTM)
@Data
@FieldNameConstants(innerTypeName = "CreditsKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "credits", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("credits")
@Persistent
public abstract class Credit implements PersistentEntity, NGAccountAccess {
  @Id protected String id;
  @Trimmed @NotEmpty protected String accountIdentifier;
  @NotEmpty protected CreditStatus creditStatus;
  @NotEmpty protected int quantity;
  @NotEmpty protected long purchaseTime;
  @NotEmpty protected long expiryTime;
  @NotEmpty protected CreditType creditType;
  @NotEmpty protected ModuleType moduleType;

  protected Long creditExpiryCheckIteration;
}
