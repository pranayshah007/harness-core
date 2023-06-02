/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepRbacHelper {
  @Inject PipelineRbacHelper pipelineRbacHelper;

  public void validateResources(ContainerStepSpec containerStepInfo, Ambiance ambiance) {
    List<EntityDetail> entityDetailList = new ArrayList<>();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    String k8sConnectorRef =
        ((ContainerK8sInfra) containerStepInfo.getInfrastructure()).getSpec().getConnectorRef().getValue();

    IdentifierRef k8sConnectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(k8sConnectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail =
        EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(k8sConnectorIdentifierRef).build();
    entityDetailList.add(entityDetail);

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }
}
