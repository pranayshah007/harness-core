/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.aws.delegate.AwsIamHelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.InstanceProfile;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesRequest;
import com.amazonaws.services.identitymanagement.model.ListInstanceProfilesResult;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Slf4j
@OwnedBy(CDP)
public class AwsIamHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsIamHelperServiceDelegate {
  @VisibleForTesting
  AmazonIdentityManagementClient getAmazonIdentityManagementClient(AwsConfig awsConfig) {
    AmazonIdentityManagementClientBuilder builder =
        AmazonIdentityManagementClient.builder().withRegion(getRegion(awsConfig));
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonIdentityManagementClient) builder.build();
  }

  @Override
  public Map<String, String> listIAMRoles(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonIdentityManagementClient> closeableAmazonIdentityManagementClient =
             new CloseableAmazonWebServiceClient(getAmazonIdentityManagementClient(awsConfig))) {
      Map<String, String> result = new HashMap<>();
      String nextMarker = null;
      do {
        ListRolesRequest listRolesRequest = new ListRolesRequest().withMaxItems(400).withMarker(nextMarker);
        tracker.trackIAMCall("List Roles");
        ListRolesResult listRolesResult =
            closeableAmazonIdentityManagementClient.getClient().listRoles(listRolesRequest);
        listRolesResult.getRoles().forEach(role -> result.put(role.getArn(), role.getRoleName()));
        nextMarker = listRolesResult.getMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listIAMRoles", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyMap();
  }

  @Override
  public List<String> listIamInstanceRoles(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonIdentityManagementClient> closeableAmazonIdentityManagementClient =
             new CloseableAmazonWebServiceClient(getAmazonIdentityManagementClient(awsConfig))) {
      List<String> result = new ArrayList<>();
      String nextMarker = null;
      ListInstanceProfilesRequest listInstanceProfilesRequest;
      ListInstanceProfilesResult listInstanceProfilesResult;
      do {
        listInstanceProfilesRequest = new ListInstanceProfilesRequest().withMarker(nextMarker);
        tracker.trackIAMCall("List Instance Profiles");
        listInstanceProfilesResult =
            closeableAmazonIdentityManagementClient.getClient().listInstanceProfiles(listInstanceProfilesRequest);
        result.addAll(listInstanceProfilesResult.getInstanceProfiles()
                          .stream()
                          .map(InstanceProfile::getInstanceProfileName)
                          .collect(toList()));
        nextMarker = listInstanceProfilesResult.getMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listIamInstanceRoles", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }
}
