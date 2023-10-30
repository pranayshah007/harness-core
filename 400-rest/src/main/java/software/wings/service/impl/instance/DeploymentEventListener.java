/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.FeatureName.INSTANCE_SYNC_V2_CG;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ff.FeatureFlagService;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.api.DeploymentEvent;
import software.wings.instancesyncv2.CgInstanceSyncServiceV2;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Receives all the completed phases and their deployment info and fetches from the corresponding servers.
 * The instance information is used in the service and infrastructure dashboards.
 * @author rktummala on 02/04/18
 *
 * For sender information,
 * @see software.wings.beans.CanaryWorkflowExecutionAdvisor
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
public class DeploymentEventListener extends QueueListener<DeploymentEvent> {
  @Inject private InstanceHelper instanceHelper;
  @Inject private CgInstanceSyncServiceV2 instanceSyncServiceV2;
  @Inject private FeatureFlagService featureFlagService;

  @Inject
  public DeploymentEventListener(QueueConsumer<DeploymentEvent> queueConsumer) {
    super(queueConsumer, false);
  }

  /* (non-Javadoc)
   * @see software.wings.core.queue.QueueListener#onMessage(software.wings.core.queue.Queuable)
   */
  @Override
  public void onMessage(DeploymentEvent deploymentEvent) {
    if (CollectionUtils.isNotEmpty(deploymentEvent.getDeploymentSummaries())) {
      String accountId = deploymentEvent.getDeploymentSummaries().get(0).getAccountId();
      if (featureFlagService.isEnabled(INSTANCE_SYNC_V2_CG, accountId)) {
        log.info("[INSTANCE_SYNC_V2_CG] Sending deployment event: [{}] to new instance sync flow for accountId: [{}]",
            deploymentEvent.getId(), accountId);
        try {
          instanceSyncServiceV2.handleInstanceSync(deploymentEvent);
          return;
        } catch (Exception e) {
          log.debug("[INSTANCE_SYNC_V2_CG] Exception for handling deployment event. Falling back to old flow.", e);
        }
      }
    }
    instanceHelper.processDeploymentEvent(deploymentEvent);
  }
}
