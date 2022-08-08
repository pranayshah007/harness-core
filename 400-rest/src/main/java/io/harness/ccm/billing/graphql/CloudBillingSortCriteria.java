/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.billing.preaggregated.PreAggregateConstants;

import software.wings.graphql.schema.type.aggregation.QLSortOrder;

import com.hazelcast.internal.util.Preconditions;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SqlObject;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class CloudBillingSortCriteria {
  private CloudSortType sortType;
  private QLSortOrder sortOrder;

  public SqlObject toOrderObject() {
    Preconditions.checkNotNull(sortOrder, "Sort Order is missing column name.");
    if (sortType == null || sortOrder == null) {
      return null;
    }

    String orderIdentifier = null;
    switch (sortType) {
      case Time:
        orderIdentifier = PreAggregateConstants.startTimeTruncatedConstant;
        break;
      case gcpCost:
        orderIdentifier = PreAggregateConstants.entityConstantGcpCost;
        break;
      case gcpProjectId:
        orderIdentifier = PreAggregateConstants.entityConstantGcpProjectId;
        break;
      case gcpProduct:
        orderIdentifier = PreAggregateConstants.entityConstantGcpProduct;
        break;
      case gcpSkuId:
        orderIdentifier = PreAggregateConstants.entityConstantGcpSkuId;
        break;
      case gcpSkuDescription:
        orderIdentifier = PreAggregateConstants.entityConstantGcpSku;
        break;
      case awsBlendedCost:
        orderIdentifier = PreAggregateConstants.entityConstantAwsBlendedCost;
        break;
      case awsUnblendedCost:
        orderIdentifier = PreAggregateConstants.entityConstantAwsUnBlendedCost;
        break;
      case awsService:
        orderIdentifier = PreAggregateConstants.entityConstantAwsService;
        break;
      case awsLinkedAccount:
        orderIdentifier = PreAggregateConstants.entityConstantAwsLinkedAccount;
        break;
      case awsUsageType:
        orderIdentifier = PreAggregateConstants.entityConstantAwsUsageType;
        break;
      case awsInstanceType:
        orderIdentifier = PreAggregateConstants.entityConstantAwsInstanceType;
        break;
      case azureSubscriptionGuid:
        orderIdentifier = PreAggregateConstants.azureSubscriptionGuid;
        break;
      case azureResourceGroup:
        orderIdentifier = PreAggregateConstants.azureResourceGroup;
        break;
      case azureMeterCategory:
        orderIdentifier = PreAggregateConstants.azureMeterCategory;
        break;
      default:
        break;
    }

    OrderObject orderObject = null;
    switch (sortOrder) {
      case ASCENDING:
        orderObject = new OrderObject(OrderObject.Dir.ASCENDING, orderIdentifier);
        break;
      case DESCENDING:
        orderObject = new OrderObject(OrderObject.Dir.DESCENDING, orderIdentifier);
        break;
      default:
        return null;
    }

    return orderObject;
  }
}
