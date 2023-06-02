/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.rancher;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.exception.ExceptionUtils;
import io.harness.rancher.RancherListClustersResponse.RancherClusterItem;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class RancherConnectionHelperServiceImpl implements RancherConnectionHelperService {
  @Inject private RancherClusterClient rancherClusterClient;

  @Override
  public ConnectorValidationResult testRancherConnection(String rancherUrl, String bearerToken) {
    try {
      rancherClusterClient.listClusters(bearerToken, rancherUrl);
      log.info("Successfully performed listClusters action using rancher cluster {}", rancherUrl);
      return ConnectorValidationResult.builder()
          .testedAt(System.currentTimeMillis())
          .status(ConnectivityStatus.SUCCESS)
          .build();
    } catch (Exception e) {
      return ConnectorValidationResult.builder()
          .errorSummary(ExceptionUtils.getMessage(e))
          .testedAt(System.currentTimeMillis())
          .status(ConnectivityStatus.FAILURE)
          .build();
    }
  }

  @Override
  public List<String> listClusters(String rancherUrl, String bearerToken) {
    RancherListClustersResponse listClustersResponse = rancherClusterClient.listClusters(bearerToken, rancherUrl);
    List<RancherClusterItem> clustersData = listClustersResponse.getData();
    if (isEmpty(clustersData)) {
      return Collections.emptyList();
    }
    return clustersData.stream().map(RancherClusterItem::getName).collect(Collectors.toList());
  }
}
