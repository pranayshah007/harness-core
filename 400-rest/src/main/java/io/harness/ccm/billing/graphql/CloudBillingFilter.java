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

import com.google.common.base.Preconditions;
import com.healthmarketscience.sqlbuilder.Condition;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@NoArgsConstructor
@Slf4j
@OwnedBy(CE)
@TargetModule(HarnessModule._375_CE_GRAPHQL)
public class CloudBillingFilter {
  public static final String BILLING_GCP_STARTTIME = "billing/gcp/starttime";
  public static final String BILLING_GCP_ENDTIME = "billing/gcp/endtime";
  public static final String BILLING_AWS_STARTTIME = "billing/aws/starttime";
  public static final String BILLING_GCP_PROJECT = "billing/gcp/project";
  public static final String BILLING_GCP_PRODUCT = "billing/gcp/product";
  public static final String BILLING_GCP_SKU = "billing/gcp/sku";
  public static final String BILLING_GCP_BILLING_ACCOUNT_ID = "billing/gcp/billingaccountid";
  public static final String BILLING_REGION = "billing/region";
  public static final String BILLING_AWS_LINKED_ACCOUNT = "billing/aws/linkedAccount";
  public static final String BILLING_AWS_USAGE_TYPE = "billing/aws/usageType";
  public static final String BILLING_AWS_INSTANCE_TYPE = "billing/aws/instanceType";
  public static final String BILLING_AWS_SERVICE = "billing/aws/service";
  public static final String GCP_TIME_FILTER_ERROR = "Invalid GCP billing time filter.";
  public static final String AWS_TIME_FILTER_ERROR = "Invalid GCP billing time filter.";
  public static final String CLOUD_PROVIDER = "cloudProvider";
  public static final String BILLING_GCP_LABEL_KEY = "billing/gcp/labelsKey";
  public static final String BILLING_GCP_LABEL_VALUE = "billing/gcp/labelsValue";
  public static final String BILLING_AWS_TAG_KEY = "billing/aws/tagsKey";
  public static final String BILLING_AWS_TAG_VALUE = "billing/aws/tagsValue";
  public static final String BILLING_AWS_TAG = "billing/aws/tags";
  public static final String BILLING_GCP_LABEL = "billing/gcp/labels";

  CloudBillingTimeFilter startTime;
  CloudBillingTimeFilter endTime;
  CloudBillingTimeFilter preAggregatedTableStartTime;
  CloudBillingTimeFilter preAggregatedTableEndTime;
  CloudBillingIdFilter projectId;
  CloudBillingIdFilter product;
  CloudBillingIdFilter sku;
  CloudBillingIdFilter billingAccountId;
  CloudBillingIdFilter region;
  CloudBillingIdFilter awsLinkedAccount;
  CloudBillingIdFilter awsUsageType;
  CloudBillingIdFilter awsInstanceType;
  CloudBillingIdFilter awsService;
  CloudBillingIdFilter cloudProvider;
  CloudBillingIdFilter labelsKey;
  CloudBillingIdFilter labelsValue;
  CloudBillingIdFilter tagsKey;
  CloudBillingIdFilter tagsValue;
  CloudBillingIdFilter tags;
  CloudBillingIdFilter labels;

  public CloudBillingTimeFilter getStartTime() {
    if (startTime == null) {
      return null;
    }
    Preconditions.checkNotNull(startTime.getValue(), GCP_TIME_FILTER_ERROR);
    Preconditions.checkNotNull(startTime.getOperator(), GCP_TIME_FILTER_ERROR);
    startTime.setVariable(BILLING_GCP_STARTTIME);
    return startTime;
  }

  public CloudBillingTimeFilter getEndTime() {
    if (endTime == null) {
      return null;
    }
    Preconditions.checkNotNull(endTime.getValue(), GCP_TIME_FILTER_ERROR);
    Preconditions.checkNotNull(endTime.getOperator(), GCP_TIME_FILTER_ERROR);
    endTime.setVariable(BILLING_GCP_ENDTIME);
    return endTime;
  }

  public CloudBillingTimeFilter getPreAggregatedStartTime() {
    if (preAggregatedTableStartTime == null) {
      return null;
    }
    Preconditions.checkNotNull(preAggregatedTableStartTime.getValue(), AWS_TIME_FILTER_ERROR);
    Preconditions.checkNotNull(preAggregatedTableStartTime.getOperator(), AWS_TIME_FILTER_ERROR);
    preAggregatedTableStartTime.setVariable(BILLING_AWS_STARTTIME);
    return preAggregatedTableStartTime;
  }

  public CloudBillingTimeFilter getPreAggregatedEndTime() {
    if (preAggregatedTableEndTime == null) {
      return null;
    }
    Preconditions.checkNotNull(preAggregatedTableEndTime.getValue(), AWS_TIME_FILTER_ERROR);
    Preconditions.checkNotNull(preAggregatedTableEndTime.getOperator(), AWS_TIME_FILTER_ERROR);
    preAggregatedTableEndTime.setVariable(BILLING_AWS_STARTTIME);
    return preAggregatedTableEndTime;
  }

  public CloudBillingIdFilter getCloudProvider() {
    if (cloudProvider == null) {
      return null;
    }
    cloudProvider.setVariable(CLOUD_PROVIDER);
    return cloudProvider;
  }

  public CloudBillingIdFilter getLabelsKey() {
    if (labelsKey == null) {
      return null;
    }
    labelsKey.setVariable(BILLING_GCP_LABEL_KEY);
    return labelsKey;
  }

  public CloudBillingIdFilter getLabelsValue() {
    if (labelsValue == null) {
      return null;
    }
    labelsValue.setVariable(BILLING_GCP_LABEL_VALUE);
    return labelsValue;
  }

  public CloudBillingIdFilter getTagsKey() {
    if (tagsKey == null) {
      return null;
    }
    tagsKey.setVariable(BILLING_AWS_TAG_KEY);
    return tagsKey;
  }

  public CloudBillingIdFilter getTagsValue() {
    if (tagsValue == null) {
      return null;
    }
    tagsValue.setVariable(BILLING_AWS_TAG_VALUE);
    return tagsValue;
  }

  public CloudBillingIdFilter getConcatTags() {
    if (tags == null) {
      return null;
    }
    tags.setVariable(BILLING_AWS_TAG);
    return tags;
  }

  public CloudBillingIdFilter getConcatLabels() {
    if (labels == null) {
      return null;
    }
    labels.setVariable(BILLING_GCP_LABEL);
    return labels;
  }

  public CloudBillingIdFilter getProject() {
    if (projectId == null) {
      return null;
    }
    projectId.setVariable(BILLING_GCP_PROJECT);
    return projectId;
  }

  public CloudBillingIdFilter getProduct() {
    if (product == null) {
      return null;
    }
    product.setVariable(BILLING_GCP_PRODUCT);
    return product;
  }

  public CloudBillingIdFilter getSku() {
    if (sku == null) {
      return null;
    }
    sku.setVariable(BILLING_GCP_SKU);
    return sku;
  }

  public CloudBillingIdFilter getBillingAccountId() {
    if (billingAccountId == null) {
      return null;
    }
    billingAccountId.setVariable(BILLING_GCP_BILLING_ACCOUNT_ID);
    return billingAccountId;
  }

  public CloudBillingIdFilter getRegion() {
    if (region == null) {
      return null;
    }
    region.setVariable(BILLING_REGION);
    return region;
  }

  public CloudBillingIdFilter getService() {
    if (awsService == null) {
      return null;
    }
    awsService.setVariable(BILLING_AWS_SERVICE);
    return awsService;
  }

  public CloudBillingIdFilter getUsageType() {
    if (awsUsageType == null) {
      return null;
    }
    awsUsageType.setVariable(BILLING_AWS_USAGE_TYPE);
    return awsUsageType;
  }

  public CloudBillingIdFilter getInstanceType() {
    if (awsInstanceType == null) {
      return null;
    }
    awsInstanceType.setVariable(BILLING_AWS_INSTANCE_TYPE);
    return awsInstanceType;
  }

  public CloudBillingIdFilter getLinkedAccount() {
    if (awsLinkedAccount == null) {
      return null;
    }
    awsLinkedAccount.setVariable(BILLING_AWS_LINKED_ACCOUNT);
    return awsLinkedAccount;
  }

  public Condition toCondition() {
    if (startTime != null) {
      return getStartTime().toCondition();
    }
    if (endTime != null) {
      return getEndTime().toCondition();
    }
    if (projectId != null) {
      return getProject().toCondition();
    }
    if (product != null) {
      return getProduct().toCondition();
    }
    if (sku != null) {
      return getSku().toCondition();
    }
    if (billingAccountId != null) {
      return getBillingAccountId().toCondition();
    }
    if (region != null) {
      return getRegion().toCondition();
    }
    if (awsService != null) {
      return getService().toCondition();
    }
    if (awsUsageType != null) {
      return getUsageType().toCondition();
    }
    if (awsInstanceType != null) {
      return getInstanceType().toCondition();
    }
    if (awsLinkedAccount != null) {
      return getLinkedAccount().toCondition();
    }
    if (preAggregatedTableStartTime != null) {
      return getPreAggregatedStartTime().toCondition();
    }
    if (preAggregatedTableEndTime != null) {
      return getPreAggregatedEndTime().toCondition();
    }
    if (cloudProvider != null) {
      return getCloudProvider().toCondition();
    }
    return null;
  }

  public Condition toRawTableCondition() {
    if (startTime != null) {
      return getStartTime().toRawTableCondition();
    }
    if (endTime != null) {
      return getEndTime().toRawTableCondition();
    }
    if (projectId != null) {
      return getProject().toRawTableCondition();
    }
    if (product != null) {
      return getProduct().toRawTableCondition();
    }
    if (sku != null) {
      return getSku().toRawTableCondition();
    }
    if (billingAccountId != null) {
      return getBillingAccountId().toRawTableCondition();
    }
    if (region != null) {
      return getRegion().toRawTableCondition();
    }
    if (labelsKey != null) {
      return getLabelsKey().toRawTableCondition();
    }
    if (labelsValue != null) {
      return getLabelsValue().toRawTableCondition();
    }
    if (labels != null) {
      return getConcatLabels().toRawTableCondition();
    }
    if (preAggregatedTableStartTime != null) {
      return getPreAggregatedStartTime().toRawTableCondition();
    }
    if (preAggregatedTableEndTime != null) {
      return getPreAggregatedEndTime().toRawTableCondition();
    }
    return null;
  }

  public Condition toAwsRawTableCondition() {
    if (startTime != null) {
      return getStartTime().toAwsRawTableCondition();
    }
    if (endTime != null) {
      return getEndTime().toAwsRawTableCondition();
    }
    if (region != null) {
      return getRegion().toAwsRawTableCondition();
    }
    if (awsLinkedAccount != null) {
      return getLinkedAccount().toAwsRawTableCondition();
    }
    if (awsUsageType != null) {
      return getUsageType().toAwsRawTableCondition();
    }
    if (awsInstanceType != null) {
      return getInstanceType().toAwsRawTableCondition();
    }
    if (awsService != null) {
      return getService().toAwsRawTableCondition();
    }
    if (tagsKey != null) {
      return getTagsKey().toAwsRawTableCondition();
    }
    if (tagsValue != null) {
      return getTagsValue().toAwsRawTableCondition();
    }
    if (tags != null) {
      return getConcatTags().toAwsRawTableCondition();
    }
    if (preAggregatedTableStartTime != null) {
      return getPreAggregatedStartTime().toAwsRawTableCondition();
    }
    if (preAggregatedTableEndTime != null) {
      return getPreAggregatedEndTime().toAwsRawTableCondition();
    }
    return null;
  }
}
