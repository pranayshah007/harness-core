/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import static io.harness.ccm.AWSConnectorTestHelper.createNonEmptyObjectListing;
import static io.harness.ccm.AWSConnectorTestHelper.createReportDefinition;
import static io.harness.rule.OwnerRule.ANMOL;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.config.AwsConfig;
import io.harness.ccm.connectors.CEAWSConnectorValidator;
import io.harness.ccm.connectors.CEConnectorsHelper;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ceawsconnector.CEAwsConnectorDTO;
import io.harness.rule.Owner;

import com.amazonaws.services.costandusagereport.model.ReportDefinition;
import com.amazonaws.services.identitymanagement.model.AmazonIdentityManagementException;
import com.amazonaws.services.identitymanagement.model.EvaluationResult;
import com.amazonaws.services.s3.model.ObjectListing;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEAwsConnectorValidatorTest extends CategoryTest {
  @Mock AwsClient awsClient;
  @Mock AwsConfig awsConfig;
  @Mock CENextGenConfiguration ceNextGenConfiguration;
  @Mock CEConnectorsHelper ceConnectorsHelper;
  @Spy @InjectMocks private CEAWSConnectorValidator connectorValidator;

  private static final String ACTION = "s3:PutObject";
  private static final String REASON = "Action: s3:PutObject not allowed on Resource: *";
  private static final String RESOURCE = "*";
  private static final String MESSAGE = "message";
  private static final String CUSTOMER_BILLING_DATA_DEV = "customer-billing-data-dev";
  private static final EvaluationResult DENY_EVALUATION_RESULT = new EvaluationResult();
  private static final String MESSAGE_SUGGESTION = "Review AWS access permissions as per the documentation.";
  private static final String awsConnectorValidation = "2022-05-05T00:00:00.00Z";

  private CEAwsConnectorDTO ceAwsConnectorDTO;
  private ConnectorResponseDTO ceawsConnectorResponseDTO;

  @BeforeClass
  public static void init() {
    DENY_EVALUATION_RESULT.withEvalDecision(REASON).withEvalActionName(ACTION).withEvalResourceName(RESOURCE);
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    awsConfig.setDestinationBucket(CUSTOMER_BILLING_DATA_DEV);
    ceAwsConnectorDTO = AWSConnectorTestHelper.createCEAwsConnectorDTO();
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    doReturn(awsConfig).when(ceNextGenConfiguration).getAwsConfig();
    doReturn(awsConnectorValidation).when(ceNextGenConfiguration).getAwsConnectorCreatedInstantForPolicyCheck();
    doReturn(null).when(connectorValidator).getCredentialProvider(any(), any());
    doNothing().when(connectorValidator).validateIfBucketAndFilesPresent(any(), any(), any(), any());
    when(ceConnectorsHelper.isDataSyncCheck(any(), any(), any(), any())).thenReturn(true);
    doReturn(Collections.singletonList(new EvaluationResult().withEvalDecision("allowed")))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)

  public void testValidateAllSuccess() throws NoSuchFieldException, IllegalAccessException {
    ReportDefinition report = createReportDefinition();
    ObjectListing s3Object = createNonEmptyObjectListing();

    doReturn(Optional.of(report)).when(awsClient).getReportDefinition(any(), any());
    doReturn(s3Object).when(awsClient).getBucket(any(), any(), any());

    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateEventsSuccess() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.VISIBILITY));
    ceAwsConnectorDTO.setCurAttributes(null);
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateEventsPermissionMissing() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.VISIBILITY));
    ceAwsConnectorDTO.setCurAttributes(null);
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    doReturn(Collections.singletonList(DENY_EVALUATION_RESULT))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());

    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());

    assertThat(result.getErrors().get(0).getReason()).isEqualTo(REASON);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo(MESSAGE_SUGGESTION);
    assertThat(result.getErrors().get(0).getCode()).isEqualTo(403);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCurSuccess() throws NoSuchFieldException, IllegalAccessException {
    ceAwsConnectorDTO.setFeaturesEnabled(ImmutableList.of(CEFeatures.BILLING));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ReportDefinition report = createReportDefinition();
    ObjectListing s3Object = createNonEmptyObjectListing();

    doReturn(Optional.of(report)).when(awsClient).getReportDefinition(any(), any());
    doReturn(s3Object).when(awsClient).getBucket(any(), any(), any());
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateReportNotPresent() throws NoSuchFieldException, IllegalAccessException {
    ceAwsConnectorDTO.setFeaturesEnabled(ImmutableList.of(CEFeatures.BILLING));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ObjectListing s3Object = createNonEmptyObjectListing();

    doReturn(Optional.empty()).when(awsClient).getReportDefinition(any(), any());
    doReturn(s3Object).when(awsClient).getBucket(any(), any(), any());

    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getReason())
        .isEqualTo("Can't access cost and usage report: report_name_utsav");
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateBucketIsPresentIgnoringObjects() {
    ceAwsConnectorDTO.setFeaturesEnabled(ImmutableList.of(CEFeatures.BILLING));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ReportDefinition report = createReportDefinition();

    doReturn(Optional.of(report)).when(awsClient).getReportDefinition(any(), any());
    doReturn(null).when(awsClient).getBucket(any(), any(), any());

    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNull();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCurPermissionMissing() throws NoSuchFieldException, IllegalAccessException {
    ceAwsConnectorDTO.setFeaturesEnabled(ImmutableList.of(CEFeatures.BILLING));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ReportDefinition report = createReportDefinition();
    ObjectListing s3Object = createNonEmptyObjectListing();

    doReturn(Collections.singletonList(DENY_EVALUATION_RESULT))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());
    doReturn(Optional.of(report)).when(awsClient).getReportDefinition(any(), any());
    doReturn(s3Object).when(awsClient).getBucket(any(), any(), any());

    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo(MESSAGE_SUGGESTION);

    assertThat(result.getErrors().get(0).getCode()).isEqualTo(403);

    assertThat(result.getErrors().get(0).getReason()).isEqualTo(REASON);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateOptimizationSuccess() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.OPTIMIZATION));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateOptimizationPermissionMissing() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.OPTIMIZATION));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    doReturn(Collections.singletonList(DENY_EVALUATION_RESULT))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getReason()).isEqualTo(REASON);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo(MESSAGE_SUGGESTION);
    assertThat(result.getErrors().get(0).getCode()).isEqualTo(403);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testValidateCommitmentOrchestratorSuccess() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.COMMITMENT_ORCHESTRATOR));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testValidateCommitmentOrchestratorPermissionMissing() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.COMMITMENT_ORCHESTRATOR));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    doReturn(Collections.singletonList(DENY_EVALUATION_RESULT))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getReason()).isEqualTo(REASON);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo(MESSAGE_SUGGESTION);
    assertThat(result.getErrors().get(0).getCode()).isEqualTo(403);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testSimulatePrincipalPolicyPermissionMissing() {
    doThrow(new AmazonIdentityManagementException(MESSAGE))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);
    System.out.println(result.getErrorSummary());
    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getErrors().get(0).getMessage())
        .contains(
            "Please allow arn:aws:iam::890436954479:role/harnessCERole to perform 'iam:SimulatePrincipalPolicy' on itself");
    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getReason()).isEqualTo(MESSAGE);
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testValidateClusterOrchestratorSuccess() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.CLUSTER_ORCHESTRATOR));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());
  }

  @Test
  @Owner(developers = ANMOL)
  @Category(UnitTests.class)
  public void testValidateClusterOrchestratorPermissionMissing() {
    ceAwsConnectorDTO.setFeaturesEnabled(Collections.singletonList(CEFeatures.CLUSTER_ORCHESTRATOR));
    ceawsConnectorResponseDTO = AWSConnectorTestHelper.getCEAwsConnectorResponseDTO(ceAwsConnectorDTO);
    doReturn(Collections.singletonList(DENY_EVALUATION_RESULT))
        .when(awsClient)
        .simulatePrincipalPolicy(any(), any(), any(), any(), any());
    ConnectorValidationResult result = connectorValidator.validate(ceawsConnectorResponseDTO, null);

    assertThat(result.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
    assertThat(result.getTestedAt()).isLessThanOrEqualTo(Instant.now().toEpochMilli());

    assertThat(result.getErrors()).hasSize(1);
    assertThat(result.getErrors().get(0).getReason()).isEqualTo(REASON);
    assertThat(result.getErrors().get(0).getMessage()).isEqualTo(MESSAGE_SUGGESTION);
    assertThat(result.getErrors().get(0).getCode()).isEqualTo(403);
  }
}
