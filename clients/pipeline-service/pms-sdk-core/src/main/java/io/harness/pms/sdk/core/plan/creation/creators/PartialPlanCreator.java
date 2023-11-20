/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;

import java.util.Map;
import java.util.Set;

public interface PartialPlanCreator<T> {
  Class<T> getFieldClass();
  Map<String, Set<String>> getSupportedTypes();

  default void setExportsInNode(PlanCreationResponse response, YamlField yamlField, String yamlVersion) {
    // Once common-plan-creators are created for v1 then this method will be no-op and the logic will move to those
    // common plan-creators for v1.
    if (response.getPlanNode() == null) {
      return;
    }
    Map<String, Object> exportsMap = PlanCreatorUtilsV1.getExportsFromYamlField(yamlField, yamlVersion);
    if (EmptyPredicate.isNotEmpty(exportsMap)) {
      response.setPlanNode(response.getPlanNode().toBuilder().exports(exportsMap).build());
    }
  }

  PlanCreationResponse createPlanForField(PlanCreationContext ctx, T field);

  default String getExecutionInputTemplateAndModifyYamlField(YamlField yamlField) {
    return "";
  }

  default Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0, HarnessYamlVersion.V1);
  }
}
