/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.task;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "CDLicenseReportAccountsKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "cdLicenseReportAccounts", noClassnameStored = true)
@Document("cdLicenseReportAccounts")
@HarnessEntity(exportable = false)
@Persistent
@OwnedBy(HarnessTeam.CDP)
public class CDLicenseReportAccounts implements PersistentEntity, PersistentRegularIterable {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_id")
                 .unique(true)
                 .field(CDLicenseReportAccountsKeys.accountIdentifier)
                 .build())
        .build();
  }

  @Id @dev.morphia.annotations.Id private String id;
  private String accountIdentifier;
  @FdIndex private Long cdLicenseDailyReportIteration;

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (CDLicenseReportAccountsKeys.cdLicenseDailyReportIteration.equals(fieldName)) {
      this.cdLicenseDailyReportIteration = nextIteration;
      return;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (CDLicenseReportAccountsKeys.cdLicenseDailyReportIteration.equals(fieldName)) {
      return this.cdLicenseDailyReportIteration;
    }

    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public String getUuid() {
    return id;
  }
}
