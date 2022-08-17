/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.intfc.AwsECSClusterService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsECSHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AwsHelperResourceService;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECluster;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.NameValuePair;
import software.wings.beans.ce.CEAwsConfig;

import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.Tag;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AwsECSClusterServiceImpl implements AwsECSClusterService {
  private final AwsHelperResourceService awsHelperResourceService;
  private final AwsECSHelperService awsECSHelperService;
  private final CEClusterDao ceClusterDao;

  @Autowired
  public AwsECSClusterServiceImpl(AwsHelperResourceService awsHelperResourceService,
      AwsECSHelperService awsECSHelperService, CEClusterDao ceClusterDao) {
    this.awsHelperResourceService = awsHelperResourceService;
    this.awsECSHelperService = awsECSHelperService;
    this.ceClusterDao = ceClusterDao;
  }

  @Value
  private static class ClusterIdentifierKey {
    String accountId;
    String infraAccountId;
    String clusterName;
    String region;
  }

  @Override
  public List<CECluster> getECSCluster(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    AwsCrossAccountAttributes awsCrossAccountAttributes = ceAwsConfig.getAwsCrossAccountAttributes();
    List<NameValuePair> awsRegions = awsHelperResourceService.getAwsRegions();
    List<CECluster> clusters = new ArrayList<>();
    awsRegions.forEach(awsRegion -> {
      List<String> ecsClusters = awsECSHelperService.listECSClusters(awsRegion.getValue(), awsCrossAccountAttributes);

      // If Customers have Describe Cluster permissions enabled - we will fetch the Tags
      Map<String, List<Tag>> clusterArnTagsMap =
          getClusterArnTagsMap(awsCrossAccountAttributes, awsRegion.getValue(), ecsClusters);

      ecsClusters.forEach(ecsCluster -> {
        Map<String, String> clusterTags = null;
        if (clusterArnTagsMap.get(ecsCluster) != null) {
          clusterTags = new HashMap<>();
          for (Tag tag : clusterArnTagsMap.get(ecsCluster)) {
            clusterTags.put(encode(tag.getKey()), tag.getValue());
          }
        }
        CECluster ceCluster = CECluster.builder()
                                  .accountId(accountId)
                                  .clusterName(getNameFromArn(ecsCluster))
                                  .clusterArn(ecsCluster)
                                  .region(awsRegion.getValue())
                                  .infraAccountId(ceAwsConfig.getAwsAccountId())
                                  .infraMasterAccountId(ceAwsConfig.getAwsMasterAccountId())
                                  .parentAccountSettingId(settingId)
                                  .labels(clusterTags)
                                  .build();
        clusters.add(ceCluster);
      });
    });
    return clusters;
  }

  private String encode(String decoded) {
    return decoded.replace('.', '~');
  }

  private Map<String, List<Tag>> getClusterArnTagsMap(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String awsRegion, List<String> ecsClusters) {
    Map<String, List<Tag>> clusterArnTagsMap = new HashMap<>();
    List<Cluster> ecsClusterObjects =
        awsECSHelperService.describeECSClusters(awsRegion, awsCrossAccountAttributes, ecsClusters);
    if (!ecsClusterObjects.isEmpty()) {
      clusterArnTagsMap =
          ecsClusterObjects.stream().collect(Collectors.toMap(Cluster::getClusterArn, Cluster::getTags));
    }
    return clusterArnTagsMap;
  }

  protected void updateClusters(String accountId, String infraAccountId, List<CECluster> infraClusters) {
    Map<ClusterIdentifierKey, CECluster> infraClusterMap = createClusterMap(infraClusters);

    List<CECluster> ceExistingClusters = ceClusterDao.getByInfraAccountId(accountId, infraAccountId);
    Map<ClusterIdentifierKey, CECluster> ceExistingClusterMap = createClusterMap(ceExistingClusters);

    infraClusterMap.forEach((clusterIdentifierKey, ceCluster) -> {
      if (!ceExistingClusterMap.containsKey(clusterIdentifierKey)) {
        ceClusterDao.create(ceCluster);
      } else if (isNotEmpty(ceCluster.getLabels())) {
        ceClusterDao.upsert(ceCluster);
      }
    });
    ceExistingClusterMap.forEach((clusterIdentifierKey, ceCluster) -> {
      if (!infraClusterMap.containsKey(clusterIdentifierKey)) {
        ceClusterDao.deleteCluster(ceCluster.getUuid());
      }
    });
  }

  protected String getNameFromArn(String arn) {
    return arn.substring(arn.lastIndexOf('/') + 1);
  }

  private Map<ClusterIdentifierKey, CECluster> createClusterMap(List<CECluster> ceClusters) {
    return ceClusters.stream().collect(Collectors.toMap(ceCluster
        -> new ClusterIdentifierKey(
            ceCluster.getAccountId(), ceCluster.getInfraAccountId(), ceCluster.getClusterName(), ceCluster.getRegion()),
        Function.identity()));
  }

  private void syncCEClusters(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    List<CECluster> ecsCluster = getECSCluster(accountId, settingId, ceAwsConfig);
    updateClusters(accountId, ceAwsConfig.getAwsAccountId(), ecsCluster);
  }

  @Override
  public void syncCEClusters(CECloudAccount ceCloudAccount) {
    String accountId = ceCloudAccount.getAccountId();
    String settingId = ceCloudAccount.getMasterAccountSettingId();
    CEAwsConfig ceAwsConfig = CEAwsConfig.builder()
                                  .awsCrossAccountAttributes(ceCloudAccount.getAwsCrossAccountAttributes())
                                  .awsAccountId(ceCloudAccount.getInfraAccountId())
                                  .awsMasterAccountId(ceCloudAccount.getInfraMasterAccountId())
                                  .build();
    syncCEClusters(accountId, settingId, ceAwsConfig);
  }
}
