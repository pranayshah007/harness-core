/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.api.DeploymentEvent;
import software.wings.instancesyncv2.CgInstanceSyncServiceV2;

import com.google.inject.Inject;

/**
 * Receives all the completed phases and their deployment info and fetches from the corresponding servers.
 * The instance information is used in the service and infrastructure dashboards.
 * @author rktummala on 02/04/18
 *
 * For sender information,
 * @see software.wings.beans.CanaryWorkflowExecutionAdvisor
 */
public class DeploymentEventListener extends QueueListener<DeploymentEvent> {
  @Inject private InstanceHelper instanceHelper;
  @Inject private CgInstanceSyncServiceV2 instanceSyncServiceV2;

  @Inject
  public DeploymentEventListener(QueueConsumer<DeploymentEvent> queueConsumer) {
    super(queueConsumer, false);
  }

  /* (non-Javadoc)
   * @see software.wings.core.queue.QueueListener#onMessage(software.wings.core.queue.Queuable)
   */
  @Override
  public void onMessage(DeploymentEvent deploymentEvent) {
    instanceSyncServiceV2.handleInstanceSync(deploymentEvent);
    // instanceHelper.processDeploymentEvent(deploymentEvent);
  }
}
