/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.ce;

import static software.wings.audit.ResourceType.CE_CONNECTOR;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;

import software.wings.beans.AwsCrossAccountAttributes;
import software.wings.beans.AwsS3BucketDetails;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonTypeName("CE_AWS")
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class CEAwsConfig extends SettingValue {
  private String awsAccountId;
  private String curReportName;
  private String awsAccountType;
  private String awsMasterAccountId;
  private String masterAccountSettingId;
  private AwsS3BucketDetails s3BucketDetails;
  private AwsCrossAccountAttributes awsCrossAccountAttributes;

  @Override
  public String fetchResourceCategory() {
    return CE_CONNECTOR.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return null;
  }

  public CEAwsConfig() {
    super(SettingVariableTypes.CE_AWS.name());
  }

  public CEAwsConfig(String awsAccountId, String curReportName, String awsAccountType, String awsMasterAccountId,
      String masterAccountSettingId, AwsS3BucketDetails s3BucketDetails,
      AwsCrossAccountAttributes awsCrossAccountAttributes) {
    this();
    this.awsAccountId = awsAccountId;
    this.curReportName = curReportName;
    this.awsAccountType = awsAccountType;
    this.awsMasterAccountId = awsMasterAccountId;
    this.masterAccountSettingId = masterAccountSettingId;
    this.s3BucketDetails = s3BucketDetails;
    this.awsCrossAccountAttributes = awsCrossAccountAttributes;
  }

  public enum AWSAccountType { MASTER_ACCOUNT, LINKED_ACCOUNT }
}
