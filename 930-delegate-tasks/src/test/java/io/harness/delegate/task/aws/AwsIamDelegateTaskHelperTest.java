/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsIAMRolesResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AwsIamDelegateTaskHelperTest extends CategoryTest {
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock AwsClient awsApiHelperService;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @InjectMocks AwsIAMDelegateTaskHelper awsIamDelegateTaskHelper;

  AwsTaskParams awsTaskParams;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    awsTaskParams = AwsTaskParams.builder()
                        .awsTaskType(AwsTaskType.LIST_IAM_ROLES)
                        .awsConnector(AwsConnectorDTO.builder()
                                          .credential(AwsCredentialDTO.builder()
                                                          .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                          .config(AwsManualConfigSpecDTO.builder().build())
                                                          .build())
                                          .build())
                        .region("test-region")
                        .build();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetIAMRoleListIsSuccess() throws Exception {
    Map<String, String> roles = new HashMap<String, String>() {
      { put("iamRole-ARN", "iamRole-Name"); }
    };

    doReturn(AwsInternalConfig.builder().build())
        .when(awsNgConfigMapper)
        .createAwsInternalConfig(awsTaskParams.getAwsConnector());
    doReturn(roles).when(awsApiHelperService).listIAMRoles(any());

    AwsIAMRolesResponse awsIAMRolesResponse =
        (AwsIAMRolesResponse) awsIamDelegateTaskHelper.getIAMRoleList(awsTaskParams);

    assertThat(awsIAMRolesResponse).isNotNull();
    assertThat(awsIAMRolesResponse).isInstanceOf(AwsIAMRolesResponse.class);
    assertThat(awsIAMRolesResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(awsIAMRolesResponse.getRoles().get("iamRole-ARN")).isEqualTo("iamRole-Name");
    verify(awsApiHelperService, times(1)).listIAMRoles(any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetIAMRoleListIsThrownException() {
    doReturn(AwsInternalConfig.builder().build())
        .when(awsNgConfigMapper)
        .createAwsInternalConfig(awsTaskParams.getAwsConnector());
    doAnswer(invocation -> { throw new Exception(); }).when(awsApiHelperService).listIAMRoles(any());
    assertThatThrownBy(() -> awsIamDelegateTaskHelper.getIAMRoleList(awsTaskParams)).isInstanceOf(Exception.class);
    verify(awsApiHelperService, times(1)).listIAMRoles(any());
  }
}
