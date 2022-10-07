/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.ManifestStep;
import io.harness.cdng.manifest.steps.ManifestStepParameters;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.serializer.KryoSerializerWrapper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class IndividualManifestPlanCreator implements PartialPlanCreator<ManifestConfig> {
  @Inject
  KryoSerializerWrapper kryoSerializerWrapper;
  @Override
  public Class<ManifestConfig> getFieldClass() {
    return ManifestConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.MANIFEST_CONFIG, ManifestType.getAllManifestTypes());
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ManifestConfig manifestConfig) {
    // Currently we are not using tha yaml passed from parent. Here manifestConfig is the object which is created by the
    // yaml which may not be correct everytime. Hence, we will be using step parameters passed from parent only
    String manifestId = (String) kryoSerializerWrapper.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());
    ManifestStepParameters stepParameters = (ManifestStepParameters) kryoSerializerWrapper.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(PlanCreatorConstants.MANIFEST_STEP_PARAMETER).toByteArray());

    PlanNode manifestPlanNode =
        PlanNode.builder()
            .uuid(manifestId)
            .stepType(ManifestStep.STEP_TYPE)
            .name(PlanCreatorConstants.MANIFEST_NODE_NAME)
            .identifier(stepParameters.getIdentifier())
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(false)
            .build();

    return PlanCreationResponse.builder().planNode(manifestPlanNode).build();
  }
}