/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.entities.modules;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.iterator.PersistentCronIterable;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import java.util.ArrayList;
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
public class CIModuleLicense extends ModuleLicense implements PersistentCronIterable {
  private Integer numberOfCommitters;
  private Long cacheAllowance;
  private Integer hostingCredits;
  List<Long> nextIterations;

  @Override
  public String getUuid() {
    return this.id;
  }

  @Override
  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
    nextIterations = isEmpty(nextIterations) ? new ArrayList<>() : nextIterations;
    expandNextIterations(skipMissed, throttled, "0 0 0 1 * ? *", nextIterations);
    return nextIterations;
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    return EmptyPredicate.isEmpty(nextIterations) ? System.currentTimeMillis() : nextIterations.get(0);
  }
}
