/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.serializer.morphia;

import io.harness.ccm.budget.ApplicationBudgetScope;
import io.harness.ccm.budget.ClusterBudgetScope;
import io.harness.ccm.budget.PerspectiveBudgetScope;
import io.harness.ccm.budgetGroup.BudgetGroup;
import io.harness.ccm.commons.entities.AWSConnectorToBucketMapping;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.commons.entities.batch.BatchJobInterval;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.ccm.commons.entities.batch.CEDataCleanupRequest;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.commons.entities.batch.DataGeneratedNotification;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage;
import io.harness.ccm.commons.entities.batch.LatestClusterInfo;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.commons.entities.billing.CEGcpServiceAccount;
import io.harness.ccm.commons.entities.billing.CloudBillingTransferRun;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ecs.ECSService;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSPartialRecommendationHistogram;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSServiceRecommendation;
import io.harness.ccm.commons.entities.events.CeExceptionRecord;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.entities.k8s.K8sYaml;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sNodeRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.ccm.commons.entities.notifications.CCMNotificationSetting;
import io.harness.ccm.commons.entities.recommendations.RecommendationsIgnoreList;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

public class CECommonsMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    // batch
    set.add(BatchJobInterval.class);
    set.add(BatchJobScheduledData.class);
    set.add(CEDataCleanupRequest.class);
    set.add(CEMetadataRecord.class);
    set.add(DataGeneratedNotification.class);
    set.add(InstanceData.class);
    set.add(LastReceivedPublishedMessage.class);
    set.add(LatestClusterInfo.class);

    // billing
    set.add(BillingDataPipelineRecord.class);
    set.add(Budget.class);
    set.add(BudgetGroup.class);
    set.add(CECloudAccount.class);
    set.add(CECluster.class);
    set.add(CEGcpServiceAccount.class);
    set.add(CloudBillingTransferRun.class);
    set.add(AWSConnectorToBucketMapping.class);

    // events
    set.add(CeExceptionRecord.class);
    set.add(PublishedMessage.class);

    // k8s
    set.add(K8sWorkload.class);
    set.add(K8sYaml.class);
    set.add(ClusterRecord.class);

    // ecs
    set.add(ECSService.class);

    // recommendations
    set.add(K8sNodeRecommendation.class);
    set.add(K8sWorkloadRecommendation.class);
    set.add(PartialRecommendationHistogram.class);
    set.add(EC2Recommendation.class);
    set.add(ECSPartialRecommendationHistogram.class);
    set.add(ECSServiceRecommendation.class);
    set.add(RecommendationsIgnoreList.class);
    set.add(AzureRecommendation.class);

    // commons
    set.add(CCMNotificationSetting.class);
  }
  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("ccm.budget.ApplicationBudgetScope", ApplicationBudgetScope.class);
    h.put("ccm.budget.ClusterBudgetScope", ClusterBudgetScope.class);
    h.put("ccm.budget.PerspectiveBudgetScope", PerspectiveBudgetScope.class);
  }
}
