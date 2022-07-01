/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;

public abstract class AbstractAzureWebAppStep extends TaskExecutableWithRollbackAndRbac<AzureWebAppTaskResponse> {
  @Inject protected CDStepHelper cdStepHelper;
  @Inject protected AzureHelperService azureHelperService;
  @Inject protected StepHelper stepHelper;
  @Inject protected KryoSerializer kryoSerializer;

  protected AzureWebAppInfraDelegateConfig getAzureWebAppInfrastructure(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    AzureWebAppInfrastructureOutcome azureWebAppInfraOutcome =
        (AzureWebAppInfrastructureOutcome) cdStepHelper.getInfrastructureOutcome(ambiance);

    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        azureWebAppInfraOutcome.getConnectorRef(), accountId, orgIdentifier, projectIdentifier);

    AzureConnectorDTO connectorDTO = azureHelperService.getConnector(identifierRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(identifierRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    return AzureWebAppInfraDelegateConfig.builder()
        .appName(azureWebAppInfraOutcome.getWebApp())
        .deploymentSlot(azureWebAppInfraOutcome.getDeploymentSlot())
        .subscription(azureWebAppInfraOutcome.getSubscription())
        .resourceGroup(azureWebAppInfraOutcome.getResourceGroup())
        .encryptionDataDetails(azureHelperService.getEncryptionDetails(connectorDTO, baseNGAccess))
        .azureConnectorDTO(connectorDTO)
        .build();
  }
}
