package io.harness.ci.plan.creator.stage;

import io.harness.pms.plan.creation.PlanCreatorUtils;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class GroupPmsPlanCreator extends IntegrationStagePMSPlanCreatorV2 {
  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("group", Sets.newHashSet(PlanCreatorUtils.ANY_TYPE, "CI", "ci"));
  }
}
