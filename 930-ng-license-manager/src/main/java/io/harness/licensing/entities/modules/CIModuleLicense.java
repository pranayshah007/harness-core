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
import io.harness.data.structure.EmptyPredicate;
import io.harness.iterator.PersistentCronIterable;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
    if (expandNextIterations(skipMissed, throttled, "0 0 0 1 * ? *", nextIterations)) {
      return isNotEmpty(nextIterations) ? nextIterations : Collections.singletonList(Long.MAX_VALUE);
    }
    return Collections.singletonList(Long.MAX_VALUE);
  }

//
//  @Override
//  public List<Long> recalculateNextIterations(String fieldName, boolean skipMissing, long throttled) {
//    try {
//      long currentTime = System.currentTimeMillis();
//        nextIterations = timeRangeBasedFreezeConfigs.stream()
//                .filter(TimeRangeBasedFreezeConfig::isApplicable)
//                .map(freeze -> freeze.getTimeRange().getFrom())
//                .distinct()
//                .sorted()
//                .filter(time -> time > currentTime)
//                .collect(Collectors.toList());
//        return nextIterations;
//    } catch (Exception ex) {
//      log.error("Failed to schedule notification for governance config {}", uuid, ex);
//      throw ex;
//    }
//  }

  @Override
  public Long obtainNextIteration(String fieldName) {
      return EmptyPredicate.isEmpty(nextIterations) ? null : nextIterations.get(0);
  }
}
