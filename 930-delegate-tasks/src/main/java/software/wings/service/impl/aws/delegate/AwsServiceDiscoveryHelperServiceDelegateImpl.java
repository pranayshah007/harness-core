/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.intfc.aws.delegate.AwsServiceDiscoveryHelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryClientBuilder;
import com.amazonaws.services.servicediscovery.model.GetNamespaceRequest;
import com.amazonaws.services.servicediscovery.model.GetNamespaceResult;
import com.amazonaws.services.servicediscovery.model.GetServiceRequest;
import com.amazonaws.services.servicediscovery.model.GetServiceResult;
import com.amazonaws.services.servicediscovery.model.Namespace;
import com.amazonaws.services.servicediscovery.model.Service;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsServiceDiscoveryHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsServiceDiscoveryHelperServiceDelegate {
  @VisibleForTesting
  AWSServiceDiscovery getAmazonServiceDiscoveryClient(String region, AwsConfig awsConfig) {
    AWSServiceDiscoveryClientBuilder builder = AWSServiceDiscoveryClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return builder.build();
  }

  @Override
  public String getRecordValueForService(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String serviceId) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails, false);
      AWSServiceDiscovery client = getAmazonServiceDiscoveryClient(region, awsConfig);
      tracker.trackSDSCall("Get Service");
      GetServiceResult getServiceResult = client.getService(new GetServiceRequest().withId(serviceId));
      if (getServiceResult == null || getServiceResult.getService() == null) {
        return "";
      }
      Service service = getServiceResult.getService();
      String namespaceId = service.getDnsConfig().getNamespaceId();
      tracker.trackSDSCall("Get NameSpace");
      GetNamespaceResult getNamespaceResult = client.getNamespace(new GetNamespaceRequest().withId(namespaceId));
      if (getNamespaceResult == null || getNamespaceResult.getNamespace() == null) {
        return "";
      }
      Namespace namespace = getNamespaceResult.getNamespace();
      return format("%s.%s", service.getName(), namespace.getName());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return "";
  }
}
