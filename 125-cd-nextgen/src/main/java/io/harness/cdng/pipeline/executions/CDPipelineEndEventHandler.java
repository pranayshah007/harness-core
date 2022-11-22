/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions;
import io.harness.account.services.AccountService;
import io.harness.cdng.pipeline.helpers.CDPipelineInstrumentationHelper;
import io.harness.cdng.provision.terraform.TerraformStepHelper;
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.repositories.executions.CDAccountExecutionMetadataRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CDPipelineEndEventHandler implements OrchestrationEventHandler {
  @Inject CDAccountExecutionMetadataRepository cdAccountExecutionMetadataRepository;
  @Inject private TerraformStepHelper helper;
  @Inject CDPipelineInstrumentationHelper cdPipelineInstrumentationHelper;
  @Inject AccountService accountService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    io.harness.ng.core.dto.AccountDTO accountDTO = accountService.getAccount(accountId);
    String accountName = accountDTO.getName();
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");

    try {
      TFPlanExecutionDetailsKey fFPlanExecutionDetailsKey = helper.createTFPlanExecutionDetailsKey(ambiance);
      List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList =
          helper.getAllPipelineTFPlanExecutionDetails(fFPlanExecutionDetailsKey);
      helper.cleanupTerraformVaultSecret(ambiance, terraformPlanExecutionDetailsList, planExecutionId);
      helper.cleanupTfPlanJson(terraformPlanExecutionDetailsList);
      helper.cleanupTfPlanHumanReadable(terraformPlanExecutionDetailsList);
      helper.cleanupAllTerraformPlanExecutionDetails(fFPlanExecutionDetailsKey);
    } catch (Exception e) {
      log.error("Failure in cleaning up the TF plan Json files from the GCS Bucket: {}", e.getMessage());
    }

    cdAccountExecutionMetadataRepository.updateAccountExecutionMetadata(accountId, event.getEndTs());

    cdPipelineInstrumentationHelper.sendServiceUsedEventsForPipelineExecution(
        pipelineId, identity, accountId, accountName, orgId, projectId, planExecutionId, event);

    long currentTS = System.currentTimeMillis();
    long searchingPeriod = 30L * 24 * 60 * 60 * 1000;

    long countOfServiceInstances = cdPipelineInstrumentationHelper.getCountOfServiceInstancesDeployedInInterval(
        accountId, orgId, projectId, currentTS - searchingPeriod, currentTS);
    cdPipelineInstrumentationHelper.sendCountOfServiceInstancesEvent(
        pipelineId, identity, accountId, accountName, orgId, projectId, countOfServiceInstances);

    long countOfDistinctActiveServices =
        cdPipelineInstrumentationHelper.getCountOfDistinctActiveServicesDeployedInInterval(
            accountId, orgId, projectId, currentTS - searchingPeriod, currentTS);
    cdPipelineInstrumentationHelper.sendCountOfDistinctActiveServicesEvent(
        pipelineId, identity, accountId, accountName, orgId, projectId, countOfDistinctActiveServices);
  }
}
