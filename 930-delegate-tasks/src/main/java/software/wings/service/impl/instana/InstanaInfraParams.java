/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.delegatetasks.cv.CVConstants.VERIFICATION_HOST_PLACEHOLDER;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "InfraParamsKeys")
public class InstanaInfraParams {
  private List<String> metrics;
  private String query;

  public void validateFields(Map<String, String> errors) {
    if (isEmpty(metrics)) {
      errors.put("infraParams." + InfraParamsKeys.metrics, "select at least one metric value.");
    }
    if (isEmpty(query)) {
      errors.put("infraParams." + InfraParamsKeys.query, "query is a required field.");
    }
    if (query != null && !query.contains(VERIFICATION_HOST_PLACEHOLDER)) {
      errors.put("infraParams." + InfraParamsKeys.query, "query should contain " + VERIFICATION_HOST_PLACEHOLDER);
    }
  }
}
