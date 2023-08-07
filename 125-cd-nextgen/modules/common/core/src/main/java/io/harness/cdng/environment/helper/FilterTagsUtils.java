/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.filters.MatchType;
import io.harness.cdng.environment.filters.TagsFilter;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.tags.TagUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.GITOPS)
public class FilterTagsUtils {
  public static boolean areAllTagFiltersMatching(List<NGTag> entityTags, List<NGTag> filterTags) {
    // Safety check, if list is empty
    if (isEmpty(entityTags) || isEmpty(filterTags)) {
      return false;
    }
    return new HashSet<>(entityTags).containsAll(filterTags);
  }

  public static boolean areAnyTagFiltersMatching(List<NGTag> entityTags, List<NGTag> filterTags) {
    if (isEmpty(entityTags)) {
      return false;
    }
    return entityTags.stream().anyMatch(filterTags::contains);
  }

  public static boolean areTagsFilterMatching(List<NGTag> entityTags, TagsFilter tagsFilter) {
    String matchType = tagsFilter.getMatchType().getValue();
    Object tagsValue = tagsFilter.getTags().getValue();
    if (!(tagsValue instanceof Map<?, ?>) ) {
      throw new InvalidRequestException(String.format(
          "Invalid filter tags value found [%s]. Filter tags should be non-empty key-value pairs of string values.",
          tagsFilter.getTags().getValue()));
    }
    Map<String, String> tagsMap = tagsFilter.getTags().getValue();
    // Remove unwanted UUID from tags
    TagUtils.removeUuidFromTags(tagsMap);
    List<NGTag> filterTags = TagMapper.convertToList(tagsMap);

    if (MatchType.any.name().equals(matchType)) {
      return areAnyTagFiltersMatching(entityTags, filterTags);
    } else if (MatchType.all.name().equals(matchType)) {
      return areAllTagFiltersMatching(entityTags, filterTags);
    } else {
      throw new InvalidRequestException(String.format("TagFilter of type [%s] is not supported", matchType));
    }
  }
}
