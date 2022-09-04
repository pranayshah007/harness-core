/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;
import software.wings.beans.infrastructure.instance.key.deployment.AwsAmiDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.AwsCodeDeployDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.AwsLambdaDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.AzureVMSSDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.AzureWebAppDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.ContainerDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.CustomDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.K8sDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.PcfDeploymentKey;
import software.wings.beans.infrastructure.instance.key.deployment.SpotinstAmiDeploymentKey;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

@Data
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "deploymentSummary", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "DeploymentSummaryKeys")
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class DeploymentSummary extends Base {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_containerDeploymentInfo")
                 .field(DeploymentSummaryKeys.accountId)
                 .field(DeploymentSummaryKeys.CLUSTER_NAME_CONTAINER_DEPLOYMENT_INFO_WITH_NAMES)
                 .field(DeploymentSummaryKeys.CONTAINER_SVC_NAME_CONTAINER_DEPLOYMENT_INFO_WITH_NAMES)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("containerSvcName_inframappingId_createdAt")
                 .field(DeploymentSummaryKeys.CONTAINER_KEY_SERVICE_NAME)
                 .field(DeploymentSummaryKeys.infraMappingId)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_k8sDeploymentInfo")
                 .field(DeploymentSummaryKeys.accountId)
                 .field(DeploymentSummaryKeys.RELEASE_NAME_K8S_DEPLOYMENT_INFO)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("infraMappingId_k8sDeploymentKeyReleaseNameAndNumber")
                 .field(DeploymentSummaryKeys.infraMappingId)
                 .field(DeploymentSummaryKeys.RELEASE_NAME_K8S_DEPLOYMENT_KEY)
                 .field(DeploymentSummaryKeys.RELEASE_NUMBER_K8S_DEPLOYMENT_KEY)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAtAsc")
                 .field(DeploymentSummaryKeys.accountId)
                 .ascSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_infraMappingId_createdAtDesc")
                 .field(DeploymentSummaryKeys.accountId)
                 .field(DeploymentSummaryKeys.infraMappingId)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("inframappingId_containerlabelsAndVersion")
                 .field(DeploymentSummaryKeys.infraMappingId)
                 .field(DeploymentSummaryKeys.CONTAINER_KEY_LABELS)
                 .field(DeploymentSummaryKeys.CONTAINER_KEY_NEW_VERSION)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("inframappingId_awsCodeDeployDeploymentKeyKey_createdAtDesc")
                 .field(DeploymentSummaryKeys.infraMappingId)
                 .field(DeploymentSummaryKeys.AWS_CODE_DEPLOY_DEPLOYMENT_KEY_KEY)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("inframappingId_awsAmiDeploymentKeyAsgName_createdAtDesc")
                 .field(DeploymentSummaryKeys.infraMappingId)
                 .field(DeploymentSummaryKeys.AWS_AMI_DEPLOYMENT_KEY_ASG_NAME)
                 .descSortField(DeploymentSummaryKeys.CREATED_AT)
                 .build())
        .build();
  }

  private String accountId;
  @FdIndex private String infraMappingId;
  private String workflowId;
  private String workflowExecutionId;
  private String workflowExecutionName;
  private String pipelineExecutionId;
  private String pipelineExecutionName;
  private String stateExecutionInstanceId;
  private String artifactId;
  private String artifactName;
  private String artifactSourceName;
  private String artifactStreamId;
  private String artifactBuildNum;
  private String deployedById;
  private String deployedByName;
  private long deployedAt;
  private DeploymentInfo deploymentInfo;
  private PcfDeploymentKey pcfDeploymentKey;
  private AwsAmiDeploymentKey awsAmiDeploymentKey;
  private AwsCodeDeployDeploymentKey awsCodeDeployDeploymentKey;
  private ContainerDeploymentKey containerDeploymentKey;
  private K8sDeploymentKey k8sDeploymentKey;
  private SpotinstAmiDeploymentKey spotinstAmiDeploymentKey;
  private AwsLambdaDeploymentKey awsLambdaDeploymentKey;
  private CustomDeploymentKey customDeploymentKey;
  private AzureVMSSDeploymentKey azureVMSSDeploymentKey;
  private AzureWebAppDeploymentKey azureWebAppDeploymentKey;

  @Builder
  public DeploymentSummary(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String accountId, String infraMappingId,
      String workflowId, String workflowExecutionId, String workflowExecutionName, String pipelineExecutionId,
      String pipelineExecutionName, String stateExecutionInstanceId, String artifactId, String artifactName,
      String artifactSourceName, String artifactStreamId, String artifactBuildNum, String deployedById,
      String deployedByName, long deployedAt, DeploymentInfo deploymentInfo, PcfDeploymentKey pcfDeploymentKey,
      AwsAmiDeploymentKey awsAmiDeploymentKey, AwsCodeDeployDeploymentKey awsCodeDeployDeploymentKey,
      ContainerDeploymentKey containerDeploymentKey, SpotinstAmiDeploymentKey spotinstAmiDeploymentKey,
      AwsLambdaDeploymentKey awsLambdaDeploymentKey, K8sDeploymentKey k8sDeploymentKey,
      CustomDeploymentKey customDeploymentKey, AzureVMSSDeploymentKey azureVMSSDeploymentKey,
      AzureWebAppDeploymentKey azureWebAppDeploymentKey) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.accountId = accountId;
    this.infraMappingId = infraMappingId;
    this.workflowId = workflowId;
    this.workflowExecutionId = workflowExecutionId;
    this.workflowExecutionName = workflowExecutionName;
    this.pipelineExecutionId = pipelineExecutionId;
    this.pipelineExecutionName = pipelineExecutionName;
    this.stateExecutionInstanceId = stateExecutionInstanceId;
    this.artifactId = artifactId;
    this.artifactName = artifactName;
    this.artifactSourceName = artifactSourceName;
    this.artifactStreamId = artifactStreamId;
    this.artifactBuildNum = artifactBuildNum;
    this.deployedById = deployedById;
    this.deployedByName = deployedByName;
    this.deployedAt = deployedAt;
    this.deploymentInfo = deploymentInfo;
    this.pcfDeploymentKey = pcfDeploymentKey;
    this.awsAmiDeploymentKey = awsAmiDeploymentKey;
    this.awsCodeDeployDeploymentKey = awsCodeDeployDeploymentKey;
    this.containerDeploymentKey = containerDeploymentKey;
    this.spotinstAmiDeploymentKey = spotinstAmiDeploymentKey;
    this.awsLambdaDeploymentKey = awsLambdaDeploymentKey;
    this.k8sDeploymentKey = k8sDeploymentKey;
    this.customDeploymentKey = customDeploymentKey;
    this.azureVMSSDeploymentKey = azureVMSSDeploymentKey;
    this.azureWebAppDeploymentKey = azureWebAppDeploymentKey;
  }

  public static final class DeploymentSummaryKeys {
    private DeploymentSummaryKeys() {}

    public static final String CREATED_AT = "createdAt";
    public static final String CONTAINER_SVC_NAME_CONTAINER_DEPLOYMENT_INFO_WITH_NAMES =
        "deploymentInfo.containerSvcName";
    public static final String CLUSTER_NAME_CONTAINER_DEPLOYMENT_INFO_WITH_NAMES = "deploymentInfo.clusterName";
    public static final String RELEASE_NAME_K8S_DEPLOYMENT_INFO = "deploymentInfo.releaseName";
    public static final String RELEASE_NAME_K8S_DEPLOYMENT_KEY = "k8sDeploymentKey.releaseName";
    public static final String RELEASE_NUMBER_K8S_DEPLOYMENT_KEY = "k8sDeploymentKey.releaseNumber";
    public static final String CONTAINER_KEY_SERVICE_NAME = "containerDeploymentKey.containerServiceName";
    public static final String CONTAINER_KEY_LABELS = "containerDeploymentKey.labels";
    public static final String CONTAINER_KEY_NEW_VERSION = "containerDeploymentKey.newVersion";
    public static final String AWS_CODE_DEPLOY_DEPLOYMENT_KEY_KEY = "awsCodeDeployDeploymentKey.key";
    public static final String AWS_AMI_DEPLOYMENT_KEY_ASG_NAME = "awsAmiDeploymentKey.autoScalingGroupName";
  }
}
