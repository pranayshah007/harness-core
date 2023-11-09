/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.mapper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.EMPTY_MAP;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.common.beans.NGTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class TagMapper {
  public static List<NGTag> convertToList(Map<String, String> tags) {
    if (isEmpty(tags)) {
      return EMPTY_LIST;
    }

    return tags.entrySet()
        .stream()
        .map(e -> NGTag.builder().key(e.getKey()).value(e.getValue()).build())
        .collect(toList());
  }

  public static Map<String, String> convertToMap(List<NGTag> tags) {
    if (isEmpty(tags)) {
      return EMPTY_MAP;
    }

    return tags.stream().collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll);
  }

  public static List<NGTag> convertToListWithNull(Map<String, String> tags) {
    if (tags == null) {
      return null;
    }
    if (isEmpty(tags)) {
      return EMPTY_LIST;
    }

    return tags.entrySet()
        .stream()
        .map(e -> NGTag.builder().key(e.getKey()).value(e.getValue()).build())
        .collect(toList());
  }
}
