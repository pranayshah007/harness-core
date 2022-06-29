/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.azure.webapps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.webapp.ConnectionStringsParameters;
import io.harness.cdng.azure.webapp.ConnectionStringsStep;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class ConnectionStringsPlanCreator implements PartialPlanCreator<StoreConfigWrapper> {
  @Inject KryoSerializer kryoSerializer;
  @Override
  public Class<StoreConfigWrapper> getFieldClass() {
    return StoreConfigWrapper.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.CONNECTION_STRINGS,
        Arrays.stream(StoreConfigType.values()).map(StoreConfigType::getDisplayName).collect(Collectors.toSet()));
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, StoreConfigWrapper field) {
    String connectionStringsFileId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());
    ConnectionStringsParameters stepParameters = (ConnectionStringsParameters) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(PlanCreatorConstants.CONNECTION_STRINGS_STEP_PARAMETER).toByteArray());

    PlanNode connectionStringsPlanNode =
        PlanNode.builder()
            .uuid(connectionStringsFileId)
            .stepType(ConnectionStringsStep.STEP_TYPE)
            .name(PlanCreatorConstants.CONNECTION_STRINGS)
            .identifier(YamlTypes.CONNECTION_STRINGS)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(false)
            .build();

    return PlanCreationResponse.builder().planNode(connectionStringsPlanNode).build();
  }
}
