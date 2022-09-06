/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.infrastructure.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

/**
 *
 * @author rktummala on 09/13/17
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "containerDeploymentInfo", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class ContainerDeploymentInfo extends Base {
  private String accountId;
  private String serviceId;
  private String envId;
  private String infraMappingId;
  private String computeProviderId;
  private String workflowId;
  private String workflowExecutionId;
  private String pipelineExecutionId;
  private String stateExecutionInstanceId;
  private InstanceType instanceType;
  private String clusterName;
  private String namespace;
  private long lastVisited;

  /**
   * In case of ECS, this would be taskDefinitionArn
   * In case of Kubernetes, this would be replicationControllerName
   * This has the revision number in it.
   */
  private String containerSvcName;
  @FdIndex private String containerSvcNameNoRevision;

  @Builder
  public ContainerDeploymentInfo(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, String accountId, String serviceId,
      String envId, String infraMappingId, String computeProviderId, String workflowId, String workflowExecutionId,
      String pipelineExecutionId, String stateExecutionInstanceId, InstanceType instanceType, String clusterName,
      String namespace, long lastVisited, String containerSvcName, String containerSvcNameNoRevision) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.accountId = accountId;
    this.serviceId = serviceId;
    this.envId = envId;
    this.infraMappingId = infraMappingId;
    this.computeProviderId = computeProviderId;
    this.workflowId = workflowId;
    this.workflowExecutionId = workflowExecutionId;
    this.pipelineExecutionId = pipelineExecutionId;
    this.stateExecutionInstanceId = stateExecutionInstanceId;
    this.instanceType = instanceType;
    this.clusterName = clusterName;
    this.namespace = namespace;
    this.lastVisited = lastVisited;
    this.containerSvcName = containerSvcName;
    this.containerSvcNameNoRevision = containerSvcNameNoRevision;
  }
}
