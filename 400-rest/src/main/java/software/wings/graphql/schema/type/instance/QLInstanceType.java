/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.instance;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.ListUtils;

import software.wings.beans.infrastructure.instance.info.AutoScalingGroupInstanceInfo;
import software.wings.beans.infrastructure.instance.info.CodeDeployInstanceInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.info.EcsContainerInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.infrastructure.instance.info.KubernetesContainerInfo;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.beans.infrastructure.instance.info.PhysicalHostInstanceInfo;

import java.util.List;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public enum QLInstanceType {
  PHYSICAL_HOST_INSTANCE(ListUtils.newArrayList(PhysicalHostInstanceInfo.class)),
  EC2_INSTANCE(ListUtils.newArrayList(Ec2InstanceInfo.class)),
  AUTOSCALING_GROUP_INSTANCE(ListUtils.newArrayList(AutoScalingGroupInstanceInfo.class)),
  CODE_DEPLOY_INSTANCE(ListUtils.newArrayList(CodeDeployInstanceInfo.class)),
  ECS_CONTAINER_INSTANCE(ListUtils.newArrayList(EcsContainerInfo.class)),
  KUBERNETES_CONTAINER_INSTANCE(ListUtils.newArrayList(KubernetesContainerInfo.class, K8sPodInfo.class)),
  PCF_INSTANCE(ListUtils.newArrayList(PcfInstanceInfo.class));

  List<Class<? extends InstanceInfo>> instanceInfos;

  QLInstanceType(List<Class<? extends InstanceInfo>> instanceInfos) {
    this.instanceInfos = instanceInfos;
  }

  public List<Class<? extends InstanceInfo>> getInstanceInfos() {
    return instanceInfos;
  }
}
