/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.entities;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(GTM)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "credits", noClassnameStored = true)

@Persistent
@TypeAlias("io.harness.credit.entities.CICredit")
public class CICredit extends Credit implements PersistentRegularIterable {
  @Override
  public Long obtainNextIteration(String fieldName) {
    if (CreditsKeys.creditExpiryCheckIteration.equals(fieldName)) {
      return creditExpiryCheckIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public String getUuid() {
    return this.id;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.creditExpiryCheckIteration = nextIteration;
  }
}
