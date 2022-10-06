/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service;

import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.CLOUD_PROVIDER_ENTITY_TAGS_TABLE_NAME;
import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.DATA_SET_NAME_TEMPLATE;
import static io.harness.batch.processing.service.impl.BillingDataPipelineServiceImpl.getDataSetDescription;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.aws.AwsClientImpl;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.ng.NGConnectorHelper;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.pricing.gcp.bigquery.BQConst;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.service.intf.AWSOrganizationHelperService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;

import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.model.Tag;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.DatasetInfo;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.StandardTableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableInfo;
import com.google.cloud.bigquery.TimePartitioning;
import com.google.inject.Singleton;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class AwsAccountTagsCollectionService {
  @Autowired private ConnectorResourceClient connectorResourceClient;
  @Autowired private BatchMainConfig mainConfig;
  @Autowired private AccountShardService accountShardService;
  @Autowired AWSOrganizationHelperService awsOrganizationHelperService;
  @Autowired BigQueryService bigQueryService;
  @Autowired AwsClientImpl awsClient;
  @Autowired NGConnectorHelper ngConnectorHelper;

  public void update() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    log.info("accounts size: {}", accountIds.size());
    for (String accountId : accountIds) {
      log.info("Fetching connectors for accountId {}", accountId);
      List<ConnectorResponseDTO> nextGenAwsConnectorResponses = getNextGenAwsConnectorResponses(accountId);
      for (ConnectorResponseDTO connector : nextGenAwsConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        CEAwsConnectorDTO ceAwsConnectorDTO = (CEAwsConnectorDTO) connectorInfo.getConnectorConfig();
        try {
          processAndInsertTags(ceAwsConnectorDTO, accountId);
        } catch (Exception e) {
          log.warn("Exception processing aws tags for connectorId: {} for CCM accountId: {}",
              connectorInfo.getIdentifier(), accountId, e);
        }
      }
    }
  }

  public List<ConnectorResponseDTO> getNextGenAwsConnectorResponses(String accountId) {
    return ngConnectorHelper.getNextGenConnectors(
        accountId, Arrays.asList(ConnectorType.CE_AWS), Arrays.asList(CEFeatures.BILLING), Collections.emptyList());
  }

  public void processAndInsertTags(CEAwsConnectorDTO ceAwsConnectorDTO, String accountId) {
    String tableName = createBQTable(accountId); // This can be moved to connector creation part
    log.info("awsAccountId: {}, roleArn: {}, externalId: {}", ceAwsConnectorDTO.getAwsAccountId(),
        ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(),
        ceAwsConnectorDTO.getCrossAccountAccess().getExternalId());
    AWSOrganizationsClient awsOrganizationsClient =
        awsClient.getAWSOrganizationsClient(ceAwsConnectorDTO.getCrossAccountAccess().getCrossAccountRoleArn(),
            ceAwsConnectorDTO.getCrossAccountAccess().getExternalId(),
            mainConfig.getAwsS3SyncConfig().getAwsAccessKey(), mainConfig.getAwsS3SyncConfig().getAwsSecretKey());
    // We want to fetch tags for all child accounts as well
    List<com.amazonaws.services.organizations.model.Account> awsAccountList =
        awsOrganizationHelperService.listAwsAccounts(awsOrganizationsClient);
    List<Tag> tagsList = null;
    for (com.amazonaws.services.organizations.model.Account awsAccount : awsAccountList) {
      // Process tags for each account
      try {
        log.info("Fetching tags list for aws accountId {}", awsAccount.getId());
        tagsList = awsOrganizationHelperService.listAwsAccountTags(awsOrganizationsClient, awsAccount.getId());
      } catch (Exception ex) {
        log.warn("Issue in fetching tags ", ex);
        tagsList = null;
      }
      insertInBQ(tableName, "AWS", awsAccount.getId(), "account", awsAccount.getName(), tagsList);
    }
  }

  public String createBQTable(String accountId) {
    String accountName = accountId;
    String dataSetName =
        String.format(DATA_SET_NAME_TEMPLATE, BillingDataPipelineUtils.modifyStringToComplyRegex(accountId));
    String description = getDataSetDescription(accountId, accountName);
    BigQuery bigquery = bigQueryService.get();
    String tableName = format("%s.%s.%s", mainConfig.getBillingDataPipelineConfig().getGcpProjectId(), dataSetName,
        CLOUD_PROVIDER_ENTITY_TAGS_TABLE_NAME);
    log.info("DatasetName: {} , TableName {}", dataSetName, tableName);
    try {
      DatasetInfo datasetInfo = DatasetInfo.newBuilder(dataSetName).setDescription(description).build();
      Dataset createdDataSet = bigquery.create(datasetInfo);
      log.info("Dataset created {}", createdDataSet);
      bigquery.create(getCloudProviderEntityTagsTableInfo(dataSetName));
      log.info("Table created {}", CLOUD_PROVIDER_ENTITY_TAGS_TABLE_NAME);
    } catch (BigQueryException bigQueryEx) {
      // dataset/table already exists.
      if (bigquery.getTable(TableId.of(dataSetName, CLOUD_PROVIDER_ENTITY_TAGS_TABLE_NAME)) == null) {
        bigquery.create(getCloudProviderEntityTagsTableInfo(dataSetName));
        log.info("Table created {}", CLOUD_PROVIDER_ENTITY_TAGS_TABLE_NAME);
        return tableName;
      }
      log.warn("Error code {}:", bigQueryEx.getCode(), bigQueryEx);
    }
    return tableName;
  }

  public void insertInBQ(String tableName, String cloudProviderId, String entityId, String entityType,
      String entityName, List<Tag> tagsList) {
    String tagsBQFormat = getTagsBQFormat(tagsList);
    String formattedQuery =
        format(BQConst.CLOUD_PROVIDER_ENTITY_TAGS_INSERT, tableName, cloudProviderId, entityId, entityType, tableName,
            cloudProviderId, entityId, entityType, entityName, tagsBQFormat, Instant.now().toString());
    log.info("Inserting tags in BQ for entityId: {} query: '{}'", entityId, formattedQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formattedQuery).build();
    try {
      bigQueryService.get().query(queryConfig);
      log.info("Inserted tags in BQ for entityId: {}", entityId);
    } catch (BigQueryException | InterruptedException bigQueryException) {
      log.warn("Error: ", bigQueryException);
    }
  }

  public String getTagsBQFormat(List<Tag> tagsList) {
    StringBuilder tagsBQFormat = new StringBuilder();
    if (isNotEmpty(tagsList)) {
      tagsBQFormat.append("ARRAY[");
      String prefix = "";
      for (Tag tag : tagsList) {
        tagsBQFormat.append(prefix);
        prefix = ",";
        tagsBQFormat.append(format(" STRUCT('%s', '%s')", tag.getKey(), tag.getValue()));
      }
      tagsBQFormat.append(']');
      return tagsBQFormat.toString();
    } else {
      return null;
    }
  }

  protected static TableInfo getCloudProviderEntityTagsTableInfo(String dataSetName) {
    TableId tableId = TableId.of(dataSetName, CLOUD_PROVIDER_ENTITY_TAGS_TABLE_NAME);
    TimePartitioning partitioning =
        TimePartitioning.newBuilder(TimePartitioning.Type.DAY).setField("updatedAt").build();
    Schema schema = Schema.of(Field.of("cloudProviderId", StandardSQLTypeName.STRING),
        Field.of("updatedAt", StandardSQLTypeName.TIMESTAMP), Field.of("entityType", StandardSQLTypeName.STRING),
        Field.of("entityId", StandardSQLTypeName.STRING), Field.of("entityName", StandardSQLTypeName.STRING),
        Field
            .newBuilder("labels", StandardSQLTypeName.STRUCT, Field.of("key", StandardSQLTypeName.STRING),
                Field.of("value", StandardSQLTypeName.STRING))
            .setMode(Field.Mode.REPEATED)
            .build());
    StandardTableDefinition tableDefinition =
        StandardTableDefinition.newBuilder().setSchema(schema).setTimePartitioning(partitioning).build();
    return TableInfo.newBuilder(tableId, tableDefinition).build();
  }
}
