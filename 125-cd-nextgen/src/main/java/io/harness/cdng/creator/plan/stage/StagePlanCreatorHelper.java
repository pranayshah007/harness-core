/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;
import static java.lang.Boolean.parseBoolean;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.infrastructure.InfrastructurePmsPlanCreator;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class StagePlanCreatorHelper {
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  @Inject private NGSettingsClient settingsClient;
  @Inject private KryoSerializer kryoSerializer;

  private static final String PROJECT_SCOPED_RESOURCE_CONSTRAINT_SETTING_ID =
      "project_scoped_resource_constraint_queue";

  public List<AdviserObtainment> addResourceConstraintDependencyWithWhenCondition(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, YamlField specField,
      PlanCreationContext context, boolean isProjectScopedResourceConstraintQueue) {
    return InfrastructurePmsPlanCreator.addResourceConstraintDependency(
        planCreationResponseMap, specField, kryoSerializer, context, isProjectScopedResourceConstraintQueue);
  }

  public boolean isProjectScopedResourceConstraintQueueByFFOrSetting(PlanCreationContext ctx) {
    return featureFlagHelperService.isEnabled(
               ctx.getAccountIdentifier(), FeatureName.CDS_PROJECT_SCOPED_RESOURCE_CONSTRAINT_QUEUE)
        || parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                PROJECT_SCOPED_RESOURCE_CONSTRAINT_SETTING_ID, ctx.getAccountIdentifier(), null, null))
                            .getValue());
  }
}
