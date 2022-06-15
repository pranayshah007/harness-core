/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc;

import software.wings.beans.AwsCrossAccountAttributes;

import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import java.util.List;

public interface AwsECSHelperService {
  List<String> listECSClusters(String region, AwsCrossAccountAttributes awsCrossAccountAttributes);

  List<Service> listServicesForCluster(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String region, String cluster);

  List<ContainerInstance> listContainerInstancesForCluster(
      AwsCrossAccountAttributes awsCrossAccountAttributes, String region, String cluster);

  List<String> listTasksArnForService(AwsCrossAccountAttributes awsCrossAccountAttributes, String region,
      String cluster, String service, DesiredStatus desiredStatus);

  List<Task> listTasksForService(AwsCrossAccountAttributes awsCrossAccountAttributes, String region, String cluster,
      String service, DesiredStatus desiredStatus);

  List<Cluster> describeECSClusters(
      String value, AwsCrossAccountAttributes awsCrossAccountAttributes, List<String> ecsClusters);
}
