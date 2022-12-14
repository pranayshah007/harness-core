/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spot.elastigroup.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.spotconnector.SpotCapabilityHelper;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class ElastigroupRollbackTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  private String elastigroupNamePrefix;
  private List<ElastiGroup> prevElastigroups;
  private boolean blueGreen;
  private SpotConnectorDTO spotConnector;
  private List<EncryptedDataDetail> encryptionDetails;
  private int timeout;

  // Conditional fields. They will be populated only if Setup was successful
  private ElastiGroup newElastigroup;
  private ElastiGroup oldElastigroup;
  private List<LoadBalancerDetailsForBGDeployment> loadBalancerDetailsForBGDeployments;
  private String awsRegion;
  private ConnectorInfoDTO awsConnectorInfo;
  private List<EncryptedDataDetail> awsEncryptedDetails;

  public boolean isSetupSuccessful() {
    return newElastigroup != null;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        encryptionDetails, maskingEvaluator));
    capabilities.addAll(SpotCapabilityHelper.fetchRequiredExecutionCapabilities(spotConnector, maskingEvaluator));

    return capabilities;
  }
}
