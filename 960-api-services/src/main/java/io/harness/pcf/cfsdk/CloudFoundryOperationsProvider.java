/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfsdk;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.reactor.ConnectionContext;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class CloudFoundryOperationsProvider {
  @Inject private ConnectionContextProvider connectionContextProvider;
  @Inject private CloudFoundryClientProvider cloudFoundryClientProvider;

  public CloudFoundryOperationsWrapper getCloudFoundryOperationsWrapper(CfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException {
    try {
      ConnectionContext connectionContext = connectionContextProvider.getConnectionContext(pcfRequestConfig);
      CloudFoundryOperations cloudFoundryOperations =
          DefaultCloudFoundryOperations.builder()
              .cloudFoundryClient(cloudFoundryClientProvider.getCloudFoundryClient(pcfRequestConfig, connectionContext))
              .organization(pcfRequestConfig.getOrgName())
              .space(pcfRequestConfig.getSpaceName())
              .build();

      return CloudFoundryOperationsWrapper.builder()
          .cloudFoundryOperations(cloudFoundryOperations)
          .connectionContext(connectionContext)
          .build();
    } catch (Exception e) {
      throw new PivotalClientApiException("Exception while creating CloudFoundryOperations: " + e.getMessage(), e);
    }
  }
}
