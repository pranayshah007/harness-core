/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.CodeDeployDeploymentInfo;

import com.amazonaws.services.codedeploy.model.CreateDeploymentRequest;
import com.amazonaws.services.codedeploy.model.RevisionLocation;
import java.util.List;

/**
 * Created by anubhaw on 6/22/17.
 */
@OwnedBy(CDP)
public interface AwsCodeDeployService {
  /**
   * List applications list.
   *
   * @param region               the region
   * @param cloudProviderSetting the cloud provider setting
   * @param encryptionDetails
   * @return the list
   */
  List<String> listApplications(
      String region, SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptionDetails);

  /**
   * List deployment group list.
   *
   * @param region               the region
   * @param appName              the app name
   * @param cloudProviderSetting the cloud provider setting
   * @param encryptedDataDetails
   * @return the list
   */
  List<String> listDeploymentGroup(String region, String appName, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails);

  /**
   * List deployment configuration list.
   *
   * @param region               the region
   * @param cloudProviderSetting the cloud provider setting
   * @param encryptedDataDetails
   * @return the list
   */
  List<String> listDeploymentConfiguration(
      String region, SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails);

  /**
   * Deploy application.
   *
   * @param region                  the region
   * @param cloudProviderSetting    the cloud provider setting
   * @param encryptedDataDetails
   *@param createDeploymentRequest the create deployment request
   * @param executionLogCallback    the execution log callback   @return the code deploy deployment info
   */
  CodeDeployDeploymentInfo deployApplication(String region, SettingAttribute cloudProviderSetting,
      List<EncryptedDataDetail> encryptedDataDetails, CreateDeploymentRequest createDeploymentRequest,
      ExecutionLogCallback executionLogCallback, int timout);

  /**
   * Gets application current revision info.
   *
   * @param region               the region
   * @param appName              the app name
   * @param cloudProviderSetting the cloud provider setting
   * @param encryptedDataDetails
   * @return the application current revision info
   */
  RevisionLocation getApplicationRevisionList(String region, String appName, String deploymentGroupName,
      SettingAttribute cloudProviderSetting, List<EncryptedDataDetail> encryptedDataDetails);
}
