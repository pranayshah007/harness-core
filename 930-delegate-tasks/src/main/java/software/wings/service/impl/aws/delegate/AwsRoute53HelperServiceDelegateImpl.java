/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.service.impl.aws.model.AwsRoute53HostedZoneData;
import software.wings.service.intfc.aws.delegate.AwsRoute53HelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.HostedZone;
import com.amazonaws.services.route53.model.ListHostedZonesResult;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsRoute53HelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsRoute53HelperServiceDelegate {
  @VisibleForTesting
  AmazonRoute53 getAmazonRoute53Client(String region, AwsConfig awsConfig) {
    AmazonRoute53ClientBuilder builder = AmazonRoute53ClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return builder.build();
  }

  @Override
  public List<AwsRoute53HostedZoneData> listHostedZones(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
      AmazonRoute53 client = getAmazonRoute53Client(region, awsConfig);
      tracker.trackR53Call("List Hosted Zones");
      ListHostedZonesResult listHostedZonesResult = client.listHostedZones();
      List<HostedZone> hostedZones = listHostedZonesResult.getHostedZones();
      if (isNotEmpty(hostedZones)) {
        return hostedZones.stream()
            .map(zone
                -> AwsRoute53HostedZoneData.builder().hostedZoneId(zone.getId()).hostedZoneName(zone.getName()).build())
            .collect(toList());
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return emptyList();
  }

  @Override
  public void upsertRoute53ParentRecord(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String parentRecordName, String parentRecordHostedZoneId, int blueServiceWeight, String blueServiceRecord,
      int greenServiceWeight, String greenServiceRecord, int ttl) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
      AmazonRoute53 client = getAmazonRoute53Client(region, awsConfig);
      ChangeResourceRecordSetsRequest changeResourceRecordSetsRequest =
          new ChangeResourceRecordSetsRequest()
              .withHostedZoneId(parentRecordHostedZoneId)
              .withChangeBatch(new ChangeBatch().withChanges(
                  new Change().withAction("UPSERT").withResourceRecordSet(
                      new ResourceRecordSet()
                          .withName(parentRecordName)
                          .withType("CNAME")
                          .withTTL(ttl > 0 ? (long) ttl : 60L)
                          .withSetIdentifier("Harness-Blue")
                          .withWeight((long) blueServiceWeight)
                          .withResourceRecords(new ResourceRecord().withValue(blueServiceRecord))),
                  new Change().withAction("UPSERT").withResourceRecordSet(
                      new ResourceRecordSet()
                          .withName(parentRecordName)
                          .withType("CNAME")
                          .withTTL(ttl > 0 ? (long) ttl : 60L)
                          .withSetIdentifier("Harness-Green")
                          .withWeight((long) greenServiceWeight)
                          .withResourceRecords(new ResourceRecord().withValue(greenServiceRecord)))));
      tracker.trackR53Call("Upsert Resource Record Sets");
      client.changeResourceRecordSets(changeResourceRecordSetsRequest);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
  }
}
