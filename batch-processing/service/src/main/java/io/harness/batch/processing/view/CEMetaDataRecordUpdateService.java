/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.view;

import static io.harness.batch.processing.billing.tasklet.BillingDataGeneratedMailTasklet.DATA_TYPE;
import static io.harness.batch.processing.billing.tasklet.BillingDataGeneratedMailTasklet.FIRST_DATA_RECEIVED;
import static io.harness.batch.processing.billing.tasklet.BillingDataGeneratedMailTasklet.MODULE;
import static io.harness.telemetry.Destination.AMPLITUDE;
import static java.util.Collections.singletonList;

import com.google.common.collect.ImmutableMap;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.anomaly.url.HarnessNgUrl;
import io.harness.ccm.cluster.entities.CEUserInfo;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord.CEMetadataRecordBuilder;
import io.harness.ccm.commons.entities.batch.DataGeneratedNotification;
import io.harness.ccm.views.dto.DefaultViewIdDto;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.ff.FeatureFlagService;
import io.harness.notification.Team;
import io.harness.notification.dtos.NotificationChannelDTO;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.notifications.NotificationResourceClient;
import io.harness.rest.RestResponse;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import org.apache.http.client.utils.URIBuilder;
import org.checkerframework.checker.nullness.Opt;
import retrofit2.Response;
import software.wings.beans.SettingAttribute;
import software.wings.beans.User;
import software.wings.beans.security.UserGroup;
import software.wings.security.PermissionAttribute;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Singleton;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class CEMetaDataRecordUpdateService {
  @Autowired private AccountShardService accountShardService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private BigQueryHelperService bigQueryHelperService;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private CEViewFolderService ceViewFolderService;
  @Autowired private CEViewService ceViewService;
  @Autowired private CEMetadataRecordDao metadataRecordDao;
  @Autowired TelemetryReporter telemetryReporter;
  @Autowired private NGConnectorHelper ngConnectorHelper;
  @Autowired private NotificationResourceClient notificationResourceClient;
  @Autowired private BatchMainConfig mainConfiguration;

  public void updateCloudProviderMetadata() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    accountIds.forEach(this::updateCloudProviderMetadata);
  }

  private void updateCloudProviderMetadata(String accountId) {
    try {
      List<SettingAttribute> ceConnectors = cloudToHarnessMappingService.getCEConnectors(accountId);
      boolean isAwsConnectorPresent = ceConnectors.stream().anyMatch(
          connector -> connector.getValue().getType().equals(SettingVariableTypes.CE_AWS.toString()));

      boolean isGCPConnectorPresent = ceConnectors.stream().anyMatch(
          connector -> connector.getValue().getType().equals(SettingVariableTypes.CE_GCP.toString()));

      boolean isAzureConnectorPresent = ceConnectors.stream().anyMatch(
          connector -> connector.getValue().getType().equals(SettingVariableTypes.CE_AZURE.toString()));

      List<ConnectorType> connectorTypes =
          Arrays.asList(ConnectorType.CE_AWS, ConnectorType.CE_AZURE, ConnectorType.GCP_CLOUD_COST);
      List<ConnectorResponseDTO> nextGenConnectorResponses = ngConnectorHelper.getNextGenConnectors(
          accountId, connectorTypes, Arrays.asList(CEFeatures.BILLING), Collections.emptyList());

      isAwsConnectorPresent =
          updateConnectorPresent(isAwsConnectorPresent, ConnectorType.CE_AWS, nextGenConnectorResponses);
      isGCPConnectorPresent =
          updateConnectorPresent(isGCPConnectorPresent, ConnectorType.GCP_CLOUD_COST, nextGenConnectorResponses);
      isAzureConnectorPresent =
          updateConnectorPresent(isAzureConnectorPresent, ConnectorType.CE_AZURE, nextGenConnectorResponses);

      CEMetadataRecordBuilder ceMetadataRecordBuilder =
          CEMetadataRecord.builder().accountId(accountId).awsDataPresent(false).gcpDataPresent(false).azureDataPresent(
              false);

      if (isAwsConnectorPresent || isGCPConnectorPresent || isAzureConnectorPresent) {
        bigQueryHelperService.updateCloudProviderMetaData(accountId, ceMetadataRecordBuilder);
      }

      CEMetadataRecord ceMetadataRecord = ceMetadataRecordBuilder.awsConnectorConfigured(isAwsConnectorPresent)
                                              .gcpConnectorConfigured(isGCPConnectorPresent)
                                              .azureConnectorConfigured(isAzureConnectorPresent)
                                              .build();

      if (ceMetadataRecord.getAwsDataPresent() || ceMetadataRecord.getAzureDataPresent()
          || ceMetadataRecord.getGcpDataPresent()) {
        CEMetadataRecord currentCEMetadataRecord = metadataRecordDao.getByAccountId(accountId);
        Boolean isSegmentDataReadyEventSent = currentCEMetadataRecord.getSegmentDataReadyEventSent();
        if (isSegmentDataReadyEventSent == null || !isSegmentDataReadyEventSent) {
          HashMap<String, Object> properties = new HashMap<>();
          properties.put(MODULE, "CCM");
          properties.put(DATA_TYPE, "CLOUD");
          telemetryReporter.sendTrackEvent(FIRST_DATA_RECEIVED, null, accountId, properties,
              Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
          ceMetadataRecord.setSegmentDataReadyEventSent(true);
        }
        if (null == ceMetadataRecord.getDataGeneratedForCloudProvider() || !ceMetadataRecord.getDataGeneratedForCloudProvider()) {
          try {
            ConnectorType connectorType = checkConnectorType(ceMetadataRecord);
            ConnectorInfoDTO connector = getConnectorFromType(connectorType, nextGenConnectorResponses);
            sendMail(accountId, connector);
            ceMetadataRecord.setDataGeneratedForCloudProvider(true);
          } catch (URISyntaxException e) {
            log.error("Error in Cloud billing data received mail: {}", e);
          }
        }
      }

      cloudToHarnessMappingService.upsertCEMetaDataRecord(ceMetadataRecord);

      ceViewFolderService.createDefaultFolders(accountId);
      createDefaultPerspective(
          accountId, isAwsConnectorPresent, isAzureConnectorPresent, isGCPConnectorPresent, ceMetadataRecord);

    } catch (Exception ex) {
      log.error("Exception while updateCloudProviderMetadata for accountId: {}", accountId, ex);
    }
  }

  private void createDefaultPerspective(String accountId, Boolean isAwsConnectorPresent,
      Boolean isAzureConnectorPresent, Boolean isGCPConnectorPresent, CEMetadataRecord ceMetadataRecord) {
    DefaultViewIdDto defaultViewIds = ceViewService.getDefaultViewIds(accountId);
    if (isAwsConnectorPresent && ceMetadataRecord.getAwsDataPresent() && defaultViewIds.getAwsViewId() == null) {
      ceViewService.createDefaultView(accountId, ViewFieldIdentifier.AWS);
    }
    if (isAzureConnectorPresent && ceMetadataRecord.getAzureDataPresent() && defaultViewIds.getAzureViewId() == null) {
      ceViewService.createDefaultView(accountId, ViewFieldIdentifier.AZURE);
    }
    if (isGCPConnectorPresent && ceMetadataRecord.getGcpDataPresent() && defaultViewIds.getGcpViewId() == null) {
      ceViewService.createDefaultView(accountId, ViewFieldIdentifier.GCP);
    }
  }

  private boolean updateConnectorPresent(
      boolean connectorPresent, ConnectorType connectorType, List<ConnectorResponseDTO> nextGenConnectorResponses) {
    if (!connectorPresent) {
      connectorPresent = nextGenConnectorResponses.stream().anyMatch(
          connectorResponseDTO -> connectorResponseDTO.getConnector().getConnectorType().equals(connectorType));
    }
    return connectorPresent;
  }

  private void sendMail(final String accountId, final ConnectorInfoDTO connector) throws URISyntaxException {
    List<CEUserInfo> users = getUsers(accountId);
    Map<String, String> templateModel = new HashMap<>();
    templateModel.put("CONNECTOR_TYPE", connector.getConnectorType().getDisplayName());
    templateModel.put("CONNECTOR_NAME", connector.getName());
    templateModel.put("CCM_URL", HarnessNgUrl.getCCMExplorerUrl(accountId, mainConfiguration.getBaseUrl()));
    if (!users.isEmpty()) {
      users.forEach(user -> {
        templateModel.put("USER_NAME", user.getName());
        NotificationChannelDTO.NotificationChannelDTOBuilder emailChannelBuilder = NotificationChannelDTO.builder()
                .accountId(accountId)
                .emailRecipients(singletonList(user.getEmail()))
                .team(Team.OTHER)
                .templateId("email_ccm_cloud_data_ready")
                .templateData(ImmutableMap.copyOf(templateModel))
                .userGroups(Collections.emptyList());
        try {
          Response<RestResponse<NotificationResult>> response =
                  notificationResourceClient.sendNotification(accountId, emailChannelBuilder.build()).execute();
          if (!response.isSuccessful()) {
            log.error("Failed to send cloud billing data received email notification: {}",
                    (response.errorBody() != null) ? response.errorBody().string() : response.code());
          } else {
            log.info("Data generated mail sent, accountId : {}", accountId);
          }
        } catch (IOException e) {
          log.error("Cloud billing data received mail couldn't be sent ", e);
        }
      });
    } else {
      log.info("No users found in CEMetaDataRecordUpdateService, accountId : {}", accountId);
    }
  }

  private List<CEUserInfo> getUsers(final String accountId) {
    List<CEUserInfo> users = new ArrayList<>();
    List<UserGroup> userGroups = cloudToHarnessMappingService.listUserGroupsForAccount(accountId);
    List<UserGroup> ceAdminUserGroups =
            userGroups.stream()
                    .filter(userGroup -> userGroup.getAccountPermissions().getPermissions().contains(PermissionAttribute.PermissionType.CE_ADMIN))
                    .collect(Collectors.toList());
    Set<String> userIds = new HashSet<>();
    ceAdminUserGroups.forEach(userGroup -> userIds.addAll(userGroup.getMemberIds()));

    userIds.forEach(id -> {
      User user = cloudToHarnessMappingService.getUser(id);
      users.add(CEUserInfo.builder().name(user.getName()).email(user.getEmail()).build());
    });
    return users;
  }

  private ConnectorType checkConnectorType(final CEMetadataRecord ceMetadataRecord) {
    if (ceMetadataRecord.getAwsDataPresent()) {
      return ConnectorType.CE_AWS;
    } else if (ceMetadataRecord.getAzureDataPresent()) {
      return ConnectorType.CE_AZURE;
    } else {
      return ConnectorType.GCP_CLOUD_COST;
    }
  }

  private ConnectorInfoDTO getConnectorFromType(final ConnectorType type,
                                                final List<ConnectorResponseDTO> nextGenConnectorResponses) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = nextGenConnectorResponses.stream().filter(
            connectorResponse -> connectorResponse.getConnector().getConnectorType().equals(type)
    ).findFirst();
    if (connectorResponseDTO.isPresent()) {
      return connectorResponseDTO.get().getConnector();
    }
    return null;
  }
}
