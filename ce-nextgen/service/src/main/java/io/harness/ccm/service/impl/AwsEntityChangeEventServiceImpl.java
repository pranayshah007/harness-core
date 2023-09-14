/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.service.impl.ConnectorEntityChangeEventUtils.lightwingAutocudDc;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.LightwingClient;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.beans.config.AwsConfig;
import io.harness.ccm.commons.dao.AWSConnectorToBucketMappingDao;
import io.harness.ccm.commons.dao.CECloudAccountDao;
import io.harness.ccm.commons.entities.AWSConnectorToBucketMapping;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.service.intf.AWSBucketPolicyHelperService;
import io.harness.ccm.service.intf.AWSOrganizationHelperService;
import io.harness.ccm.service.intf.AwsEntityChangeEventService;
import io.harness.configuration.DeployMode;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.ceawsconnector.AwsCurAttributesDTO;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.client.NGRestUtils;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.organizations.model.AWSOrganizationsNotInUseException;
import com.amazonaws.services.organizations.model.AccessDeniedException;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class AwsEntityChangeEventServiceImpl implements AwsEntityChangeEventService {
  public static final String ACCOUNT_ID = "accountId";
  public static final String ACTION = "action";
  public static final String AWS_INFRA_ACCOUNT_ID = "awsInfraAccountId";
  public static final String AWS_CROSS_ACCOUNT_EXTERNAL_ID = "awsCrossAccountExternalId";
  public static final String AWS_CROSS_ACCOUNT_ROLE_ARN = "awsCrossAccountRoleArn";
  public static final String CONNECTOR_ID = "connectorId";
  private static final String GOOGLE_CREDENTIALS_PATH = "CE_GCP_CREDENTIALS_PATH";
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject LightwingClient lightwingClient;
  @Inject AWSOrganizationHelperService awsOrganizationHelperService;
  @Inject AWSBucketPolicyHelperService awsBucketPolicyHelperService;
  @Inject CENextGenConfiguration configuration;
  @Inject CECloudAccountDao cloudAccountDao;
  @Inject AwsClient awsClient;
  @Inject AWSConnectorToBucketMappingDao awsConnectorToBucketMappingDao;
  @Inject BigQueryService bigQueryService;

  @Override
  public boolean processAWSEntityChangeEvent(EntityChangeDTO entityChangeDTO, String action) {
    String identifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    AwsConfig awsConfig = configuration.getAwsConfig();
    CEAwsConnectorDTO ceAwsConnectorDTO;
    String destinationBucket = getDestinationBucketName(awsConfig);
    ArrayList<ImmutableMap<String, String>> entityChangeEvents = new ArrayList<>();
    log.info("processAWSEntityChangeEvent action: {}", action);
    switch (action) {
      case CREATE_ACTION:
        ceAwsConnectorDTO =
            (CEAwsConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
        lightwingAutocudDc(CREATE_ACTION, accountIdentifier, ceAwsConnectorDTO, lightwingClient, configuration);
        // Update Bucket Policy
        if (isBillingFeatureEnabled(ceAwsConnectorDTO) && validateResourcesExists(awsConfig, ceAwsConnectorDTO)) {
          awsConnectorToBucketMappingDao.upsert(AWSConnectorToBucketMapping.builder()
                                                    .accountId(accountIdentifier)
                                                    .awsConnectorIdentifier(identifier)
                                                    .destinationBucket(destinationBucket)
                                                    .build());
          awsBucketPolicyHelperService.updateBucketPolicy(
              ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(), destinationBucket,
              awsConfig.getAccessKey(), awsConfig.getSecretKey());
        }
        if (isVisibilityFeatureEnabled(ceAwsConnectorDTO)) {
          updateEventData(action, identifier, accountIdentifier,
              ceAwsConnectorDTO.getCrossAccountAccess().getExternalId(),
              ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(), ceAwsConnectorDTO.getAwsAccountId(),
              entityChangeEvents);
          EntityChangeEventServiceHelper.publishMessage(entityChangeEvents,
              configuration.getGcpConfig().getGcpProjectId(),
              configuration.getGcpConfig().getGcpAwsConnectorCrudPubSubTopic(),
              bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
        }
        updateCECloudAccountMongoCollection(accountIdentifier, identifier, ceAwsConnectorDTO, awsConfig);
        break;
      case UPDATE_ACTION:
        ceAwsConnectorDTO =
            (CEAwsConnectorDTO) getConnectorConfigDTO(accountIdentifier, identifier).getConnectorConfig();
        lightwingAutocudDc(UPDATE_ACTION, accountIdentifier, ceAwsConnectorDTO, lightwingClient, configuration);
        // Update Bucket Policy
        if (isBillingFeatureEnabled(ceAwsConnectorDTO) && validateResourcesExists(awsConfig, ceAwsConnectorDTO)) {
          awsConnectorToBucketMappingDao.upsert(AWSConnectorToBucketMapping.builder()
                                                    .accountId(accountIdentifier)
                                                    .awsConnectorIdentifier(identifier)
                                                    .destinationBucket(destinationBucket)
                                                    .build());
          awsBucketPolicyHelperService.updateBucketPolicy(
              ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(), destinationBucket,
              awsConfig.getAccessKey(), awsConfig.getSecretKey());
        }
        if (isVisibilityFeatureEnabled(ceAwsConnectorDTO)) {
          updateEventData(action, identifier, accountIdentifier,
              ceAwsConnectorDTO.getCrossAccountAccess().getExternalId(),
              ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(), ceAwsConnectorDTO.getAwsAccountId(),
              entityChangeEvents);
          EntityChangeEventServiceHelper.publishMessage(entityChangeEvents,
              configuration.getGcpConfig().getGcpProjectId(),
              configuration.getGcpConfig().getGcpAwsConnectorCrudPubSubTopic(),
              bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
        }
        updateCECloudAccountMongoCollection(accountIdentifier, identifier, ceAwsConnectorDTO, awsConfig);
        break;
      case DELETE_ACTION:
        updateEventData(action, identifier, accountIdentifier, "", "", "", entityChangeEvents);
        EntityChangeEventServiceHelper.publishMessage(entityChangeEvents,
            configuration.getGcpConfig().getGcpProjectId(),
            configuration.getGcpConfig().getGcpAwsConnectorCrudPubSubTopic(),
            bigQueryService.getCredentials(GOOGLE_CREDENTIALS_PATH));
        break;
      default:
        log.info("Not processing AWS Event, action: {}, entityChangeDTO: {}", action, entityChangeDTO);
    }
    return true;
  }

  private void updateCECloudAccountMongoCollection(
      String accountIdentifier, String identifier, CEAwsConnectorDTO ceAwsConnectorDTO, AwsConfig awsConfig) {
    // fetch AWS accounts under organization and upsert these account details in ceCloudAccount collection in mongo
    log.info("CEAwsConnectorDTO: {}", ceAwsConnectorDTO);
    List<CECloudAccount> awsAccounts = new ArrayList<>();
    try {
      awsAccounts = awsOrganizationHelperService.getAWSAccounts(accountIdentifier, identifier, ceAwsConnectorDTO,
          awsConfig.getAccessKey(), awsConfig.getSecretKey(), configuration.getCeProxyConfig());
      log.info("Number of AWS Accounts: {}, Not Processing Create AWS Account Metadata", awsAccounts.size());
    } catch (AWSOrganizationsNotInUseException ex) {
      log.info("AWSOrganizationsNotInUseException for AWS Connector: {}", ceAwsConnectorDTO.getAwsAccountId(), ex);
    } catch (AccessDeniedException accessDeniedException) {
      log.info("AccessDeniedException for AWS Connector: {}, Not Processing Create AWS Account Metadata",
          ceAwsConnectorDTO.getAwsAccountId(), accessDeniedException);
    } catch (Exception ex) {
      log.info("Exception for AWS Connector:, {}, Not Processing Create AWS Account Metadata",
          ceAwsConnectorDTO.getAwsAccountId(), ex);
    }
    for (CECloudAccount account : awsAccounts) {
      log.info("Upserting CECloudAccount: {}", account);
      log.info("New UUID: {}", cloudAccountDao.upsert(account));
    }
  }

  private void updateEventData(String action, String identifier, String accountIdentifier,
      String awsCrossAccountExternalId, String awsCrossAccountRoleArn, String awsInfraAccountId,
      ArrayList<ImmutableMap<String, String>> entityChangeEvents) {
    log.info("Visibility feature is enabled. Prepping event for pubsub");
    entityChangeEvents.add(ImmutableMap.<String, String>builder()
                               .put(ACTION, action)
                               .put(ACCOUNT_ID, accountIdentifier)
                               .put(AWS_INFRA_ACCOUNT_ID, awsInfraAccountId)
                               .put(AWS_CROSS_ACCOUNT_EXTERNAL_ID, awsCrossAccountExternalId)
                               .put(AWS_CROSS_ACCOUNT_ROLE_ARN, awsCrossAccountRoleArn)
                               .put(CONNECTOR_ID, identifier)
                               .build());
  }

  private String getDestinationBucketName(AwsConfig awsConfig) {
    if (DeployMode.isOnPrem(configuration.getDeployMode().name())) {
      return awsConfig.getDestinationBucket();
    }
    return String.format("%s-%s", awsConfig.getDestinationBucket(), awsConfig.getDestinationBucketsCount());
  }

  public ConnectorInfoDTO getConnectorConfigDTO(String accountIdentifier, String connectorIdentifierRef) {
    try {
      Optional<ConnectorDTO> connectorDTO =
          NGRestUtils.getResponse(connectorResourceClient.get(connectorIdentifierRef, accountIdentifier, null, null));

      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorIdentifierRef));
      }

      return connectorDTO.get().getConnectorInfo();
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Error while getting connector information : [%s]", connectorIdentifierRef));
    }
  }

  private boolean isBillingFeatureEnabled(CEAwsConnectorDTO ceAwsConnectorDTO) {
    List<CEFeatures> featuresEnabled = ceAwsConnectorDTO.getFeaturesEnabled();
    return featuresEnabled.contains(CEFeatures.BILLING);
  }

  private boolean isVisibilityFeatureEnabled(CEAwsConnectorDTO ceAwsConnectorDTO) {
    List<CEFeatures> featuresEnabled = ceAwsConnectorDTO.getFeaturesEnabled();
    return featuresEnabled.contains(CEFeatures.VISIBILITY);
  }

  private boolean validateResourcesExists(AwsConfig awsConfig, CEAwsConnectorDTO ceAwsConnectorDTO) {
    AWSCredentialsProvider credentialProvider =
        getCredentialProvider(ceAwsConnectorDTO.getCrossAccountAccess(), awsConfig);
    AwsCurAttributesDTO curAttributes = ceAwsConnectorDTO.getCurAttributes();
    // Report Exists
    Optional<ReportDefinition> report = awsClient.getReportDefinition(
        credentialProvider, curAttributes.getReportName(), configuration.getCeProxyConfig());
    if (!report.isPresent()) {
      return false;
    }

    return true;
  }

  public AWSCredentialsProvider getCredentialProvider(
      CrossAccountAccessDTO crossAccountAccessDTO, AwsConfig awsConfig) {
    final AWSCredentialsProvider staticBasicAwsCredentials =
        awsClient.constructStaticBasicAwsCredentials(awsConfig.getAccessKey(), awsConfig.getSecretKey());
    final AWSCredentialsProvider credentialsProvider =
        awsClient.getAssumedCredentialsProvider(staticBasicAwsCredentials,
            crossAccountAccessDTO.getCrossAccountRoleArn(), crossAccountAccessDTO.getExternalId());
    credentialsProvider.getCredentials();
    return credentialsProvider;
  }
}
