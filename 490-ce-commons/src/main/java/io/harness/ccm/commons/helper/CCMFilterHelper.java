/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMNumberFilter;
import io.harness.ccm.commons.entities.CCMOperator;
import io.harness.ccm.commons.entities.CCMStringFilter;

import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CE)
@UtilityClass
public class CCMFilterHelper {
  public CCMStringFilter buildStringFilter(CCMField field, CCMOperator operator, List<String> values) {
    return CCMStringFilter.builder().field(field).values(values).operator(operator).build();
  }

  public CCMNumberFilter buildNumberFilter(CCMField field, CCMOperator operator, Number value) {
    return CCMNumberFilter.builder().field(field).value(value).operator(operator).build();
  }
}
