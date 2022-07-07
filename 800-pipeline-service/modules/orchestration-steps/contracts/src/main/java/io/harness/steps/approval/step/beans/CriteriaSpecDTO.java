/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;

@OwnedBy(CDC)
@JsonSubTypes({
  @JsonSubTypes.Type(value = JexlCriteriaSpecDTO.class, name = CriteriaSpecTypeConstants.JEXL)
  , @JsonSubTypes.Type(value = KeyValuesCriteriaSpecDTO.class, name = CriteriaSpecTypeConstants.KEY_VALUES)
})
public interface CriteriaSpecDTO {
  @JsonIgnore boolean isEmpty();
}
