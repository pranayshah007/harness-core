/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("keyValueCriteriaSpec")
@Schema(name = "KeyValuesCriteriaSpec", description = "This contains details of Key-Value Criteria specifications")
public class KeyValuesCriteriaSpecDTO implements CriteriaSpecDTO {
  boolean matchAnyCondition;
  @NotNull List<io.harness.steps.approval.step.beans.ConditionDTO> conditions;

  @Override
  public boolean isEmpty() {
    return EmptyPredicate.isEmpty(conditions);
  }

  public static KeyValuesCriteriaSpecDTO fromKeyValueCriteria(
      KeyValuesCriteriaSpec keyValuesCriteriaSpec, boolean skipEmpty) {
    boolean matchCondition = false;
    Object matchConditionValue = keyValuesCriteriaSpec.getMatchAnyCondition().fetchFinalValue();
    if (matchConditionValue != null) {
      matchCondition = (boolean) matchConditionValue;
    }

    List<io.harness.steps.approval.step.beans.Condition> conditions = keyValuesCriteriaSpec.getConditions();
    if (EmptyPredicate.isEmpty(conditions)) {
      if (skipEmpty) {
        return KeyValuesCriteriaSpecDTO.builder()
            .matchAnyCondition(matchCondition)
            .conditions(Collections.emptyList())
            .build();
      }
      throw new InvalidRequestException("At least 1 condition is required in KeyValues criteria");
    }

    List<io.harness.steps.approval.step.beans.ConditionDTO> conditionDTOS = new ArrayList<>();
    for (Condition condition : conditions) {
      conditionDTOS.add(ConditionDTO.fromCondition(condition));
    }
    return KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(matchCondition).conditions(conditionDTOS).build();
  }
}
