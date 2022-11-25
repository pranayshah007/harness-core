package io.harness.pms.sdk.core.creator;

import io.harness.pms.contracts.plan.CreationRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class CreatorFactory {
  @Inject PlanCreatorServiceV1 planCreatorService;
  @Inject VariableCreatorServiceV1 variableCreatorService;
  @Inject FilterCreatorServiceV1 filterCreatorService;
  public CreatorServiceV1 getCreatorService(CreationRequest request) {
    switch (request.getType()) {
      case PLAN:
        return planCreatorService;
      case FILTER:
        return filterCreatorService;
      case VARIABLE:
        return variableCreatorService;
      default:
        // Handle this
        return planCreatorService;
    }
  }
}
