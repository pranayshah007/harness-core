/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet;

import static software.wings.beans.SettingAttribute.SettingCategory.CE_CONNECTOR;
import static software.wings.settings.SettingVariableTypes.CE_AWS;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ecs.service.intfc.AwsAccountService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.intfc.AwsECSClusterService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.support.intfc.AWSOrganizationHelperService;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.amazonaws.services.organizations.model.AWSOrganizationsNotInUseException;
import com.amazonaws.services.organizations.model.AccessDeniedException;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Singleton
public class AwsECSClusterSyncTasklet implements Tasklet {
  @Autowired private AwsECSClusterService awsECSClusterService;
  @Autowired private AwsAccountService awsAccountService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private CECloudAccountDao ceCloudAccountDao;
  @Autowired private NGConnectorHelper ngConnectorHelper;
  @Autowired private AWSOrganizationHelperService awsOrganizationHelperService;

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();

    List<SettingAttribute> ceConnectorList =
        cloudToHarnessMappingService.listSettingAttributesCreatedInDuration(accountId, CE_CONNECTOR, CE_AWS);
    ceConnectorList.forEach(ceConnector -> {
      CEAwsConfig ceAwsConfig = (CEAwsConfig) ceConnector.getValue();
      syncLinkedAccounts(accountId, ceConnector.getAccountId(), ceAwsConfig);
      CECloudAccount cloudAccount = CECloudAccount.builder()
                                        .accountId(accountId)
                                        .infraAccountId(ceAwsConfig.getAwsMasterAccountId())
                                        .infraMasterAccountId(ceAwsConfig.getAwsMasterAccountId())
                                        .masterAccountSettingId(ceConnector.getUuid())
                                        .awsCrossAccountAttributes(ceAwsConfig.getAwsCrossAccountAttributes())
                                        .build();
      awsECSClusterService.syncCEClusters(cloudAccount);
      List<CECloudAccount> ceCloudAccounts =
          ceCloudAccountDao.getByMasterAccountId(accountId, ceConnector.getUuid(), ceAwsConfig.getAwsMasterAccountId());
      ceCloudAccounts.forEach(ceCloudAccount -> awsECSClusterService.syncCEClusters(ceCloudAccount));
    });
    syncLinkedAccountsForNgConnectors(accountId);
    syncNGClusters(accountId);
    return null;
  }

  public void syncNGClusters(String accountId) {
    List<ConnectorResponseDTO> nextGenConnectors =
        ngConnectorHelper.getNextGenConnectors(accountId, Arrays.asList(ConnectorType.CE_AWS),
            Arrays.asList(CEFeatures.VISIBILITY), Arrays.asList(ConnectivityStatus.SUCCESS));
    log.info("Next gen ConnectorDisconnectHandler list size {}", nextGenConnectors.size());
    for (ConnectorResponseDTO connector : nextGenConnectors) {
      ConnectorInfoDTO connectorInfo = connector.getConnector();
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
      if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
        AwsCrossAccountAttributes crossAccountAttributes =
            AwsCrossAccountAttributes.builder()
                .crossAccountRoleArn(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn())
                .externalId(ceAwsConnectorDTO.getCrossAccountAccess().getExternalId())
                .build();
        CECloudAccount cloudAccount = CECloudAccount.builder()
                                          .accountId(accountId)
                                          .infraAccountId(ceAwsConnectorDTO.getAwsAccountId())
                                          .infraMasterAccountId(ceAwsConnectorDTO.getAwsAccountId())
                                          .masterAccountSettingId(connectorInfo.getIdentifier())
                                          .awsCrossAccountAttributes(crossAccountAttributes)
                                          .build();
        awsECSClusterService.syncCEClusters(cloudAccount);
      }
    }
  }

  private void syncLinkedAccountsForNgConnectors(String accountId) {
    List<ConnectorResponseDTO> nextGenBillingConnectors =
        ngConnectorHelper.getNextGenConnectors(accountId, Arrays.asList(ConnectorType.CE_AWS),
            Arrays.asList(CEFeatures.BILLING), Arrays.asList(ConnectivityStatus.SUCCESS));
    log.info("Next gen billing ConnectorDisconnectHandler list size {}", nextGenBillingConnectors.size());
    for (ConnectorResponseDTO connector : nextGenBillingConnectors) {
      ConnectorInfoDTO connectorInfo = connector.getConnector();
      CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
      if (ceAwsConnectorDTO != null && ceAwsConnectorDTO.getCrossAccountAccess() != null) {
        syncNGConnectorLinkedAccounts(accountId, connectorInfo.getIdentifier(), ceAwsConnectorDTO);
      }
    }
  }

  private void syncNGConnectorLinkedAccounts(String accountId, String settingId, CEAwsConnectorDTO ceAwsConnectorDTO) {
    try {
      awsAccountService.syncNgLinkedAccounts(accountId, settingId, ceAwsConnectorDTO);
      log.info("Synced Accounts");
    } catch (AWSOrganizationsNotInUseException ex) {
      log.info("AWSOrganizationsNotInUseException for AWS Connector: {}", ceAwsConnectorDTO.getAwsAccountId(), ex);
    } catch (AccessDeniedException accessDeniedException) {
      log.info("AccessDeniedException for AWS Connector: {}, Not Processing Create AWS Account Metadata",
          ceAwsConnectorDTO.getAwsAccountId(), accessDeniedException);
    } catch (Exception ex) {
      log.info("Exception for AWS Connector:, {}, Not Processing Create AWS Account Metadata",
          ceAwsConnectorDTO.getAwsAccountId(), ex);
    }
  }

  private void syncLinkedAccounts(String accountId, String settingId, CEAwsConfig ceAwsConfig) {
    try {
      log.info("Started syncing linked accounts");
      awsAccountService.syncLinkedAccounts(accountId, settingId, ceAwsConfig);
      log.info("Finished syncing linked accounts");
    } catch (Exception ex) {
      log.error("Exception while sync account", ex);
    }
  }
}
