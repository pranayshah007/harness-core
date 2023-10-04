/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.entities.modules;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.GTM)
@Data
@FieldNameConstants(innerTypeName = "CIModuleLicenseKeys")
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "moduleLicenses", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.license.entities.module.CIModuleLicense")
public class CIModuleLicense extends ModuleLicense implements PersistentRegularIterable {
  private Integer numberOfCommitters;
  private Long cacheAllowance;
  private Integer hostingCredits;
  List<Long> nextIterations;
  Long provisionMonthlyCICreditsIteration;

  @Override
  public String getUuid() {
    return this.id;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (CIModuleLicenseKeys.provisionMonthlyCICreditsIteration.equals(fieldName)) {
      return provisionMonthlyCICreditsIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (CIModuleLicenseKeys.provisionMonthlyCICreditsIteration.equals(fieldName)) {
      this.provisionMonthlyCICreditsIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("moduleType_status_provisionMonthlyCICreditsIteration")
                 .field(ModuleLicenseKeys.moduleType)
                 .field(ModuleLicenseKeys.status)
                 .field(CIModuleLicenseKeys.provisionMonthlyCICreditsIteration)
                 .build())
        .build();
  }
}
