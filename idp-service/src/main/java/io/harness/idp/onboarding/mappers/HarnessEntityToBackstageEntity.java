/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.onboarding.beans.BackstageCatalogEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
public interface HarnessEntityToBackstageEntity<S, T extends BackstageCatalogEntity> {
  T map(S harnessEntity);

  default String truncateName(String harnessEntityName) {
    if (harnessEntityName.length() > 63) {
      return StringUtils.truncate(harnessEntityName, 60) + "---";
    }
    return harnessEntityName;
  }

  default List<String> getTags(Map<String, String> harnessEntityTags) {
    if (harnessEntityTags == null) {
      return Collections.emptyList();
    }
    return harnessEntityTags.values()
        .stream()
        .filter(value -> value != null && !value.trim().isEmpty())
        .collect(Collectors.toList());
  }
}
