/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.request;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.expression.Expression;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.spotinst.model.ElastiGroup;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.NonFinal;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

@Data
@Builder
@OwnedBy(CDP)
public class ElastigroupSwapRouteCommandRequest
    implements ElastigroupCommandRequest {
  String accountId;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  boolean blueGreen;
  ResizeStrategy resizeStrategy;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) SpotInstConfig spotInstConfig;
  List<EncryptedDataDetail> connectorEncryptedDetails;
  private ElastiGroup newElastigroup;
  private ElastiGroup oldElastigroup;
  private String elastigroupNamePrefix;
  private String downsizeOldElastigroup;
  private int steadyStateTimeOut;
  private ConnectedCloudProvider connectedCloudProvider;
  private LoadBalancerConfig loadBalancerConfig;
  @Override
  public ConnectorInfoDTO getConnectorInfoDTO() {
    if (null != connectedCloudProvider) {
      return connectedCloudProvider.getConnectorInfoDTO();
    }
    return null;
  }

  @Override
  public List<EncryptedDataDetail> getConnectorEncryptedDetails() {
    if (null != connectedCloudProvider) {
      return connectedCloudProvider.getEncryptionDetails();
    }
    return null;
  }
}
