/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;

import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.ContainerInstanceStatus;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.Task;
import java.util.List;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public interface AwsEcsHelperServiceDelegate {
  List<String> listClusters(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region);

  /**
   * Deprecated: use software.wings.cloudprovider.aws.AwsClusterServiceImpl#getServices(java.lang.String,
   * software.wings.beans.SettingAttribute, java.util.List, java.lang.String, java.lang.String)
   *
   * The filtering logic can be provided inside that method. Saves api calls.
   */
  @Deprecated
  List<Service> listServicesForCluster(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String cluster);
  List<String> listTasksArnForService(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String cluster, String service, DesiredStatus desiredStatus);
  boolean serviceExists(SettingAttribute settingAttribute, List<EncryptedDataDetail> encryptionDetails, String region,
      String cluster, String serviceName);
  List<Task> listTasksForService(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String cluster, String service, DesiredStatus desiredStatus);
  List<ContainerInstance> listContainerInstancesForCluster(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String cluster,
      ContainerInstanceStatus containerInstanceStatus);
}
