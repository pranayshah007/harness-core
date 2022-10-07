package io.harness.cdng.creator.plan;

import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.serializer.KryoSerializerWrapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class CDStepGroupPmsPlanCreator extends StepGroupPMSPlanCreator {
  @Override
  public void addStrategyFieldDependencyIfPresent(KryoSerializerWrapper kryoSerializerWrapper, PlanCreationContext ctx, String uuid,
                                                  String name, String identifier, LinkedHashMap<String, PlanCreationResponse> responseMap,
                                                  HashMap<Object, Object> objectObjectHashMap, List<AdviserObtainment> adviserObtainmentFromMetaData) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializerWrapper, ctx, uuid, name, identifier, responseMap,
        new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField(), false), false);
  }
}
