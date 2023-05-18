/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.secretmanagement;

import static io.harness.rule.OwnerRule.NISHANT;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.SecretManagementException;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionType;

import software.wings.app.MainConfiguration;
import software.wings.beans.GcpKmsConfig;
import software.wings.resources.secretsmanagement.GcpSecretsManagerResource;
import software.wings.service.impl.security.GcpSecretsManagerServiceImpl;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.utils.AccountPermissionUtils;

import com.google.common.collect.Sets;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.Mock;

public class GcpSecretsManagerResourceTest extends CategoryTest {
  @Mock private static GcpSecretsManagerServiceImpl gcpSecretsManagerService;
  @Mock private static AccountPermissionUtils accountPermissionUtils;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private static MainConfiguration configuration;
  @Mock private static UsageRestrictionsService usageRestrictionsService;
  private GcpSecretsManagerResource gcpSecretsManagerResource;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  String accountId = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String keyName = randomAlphabetic(10);
  String keyRing = randomAlphabetic(10);
  String projectId = randomAlphabetic(10);
  String region = randomAlphabetic(10);
  String uuid = UUIDGenerator.generateUuid();
  String usageRestrictionString = randomAlphabetic(10);
  InputStream uploadedInputString = new ByteArrayInputStream("{}".getBytes());
  Set<String> delegateSelector = Sets.newHashSet(randomAlphabetic(10));

  @Before
  public void setup() {
    initMocks(this);
    when(configuration.getFileUploadLimits().getEncryptedFileLimit()).thenReturn(10L * 1024 * 1024);
    gcpSecretsManagerResource = new GcpSecretsManagerResource(
        gcpSecretsManagerService, accountPermissionUtils, configuration, usageRestrictionsService);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKmsConfigNotHarnessUser() throws IOException {
    String message = "User not allowed to update global KMS";
    when(accountPermissionUtils.checkIfHarnessUser(anyString()))
        .thenReturn(RestResponse.Builder.aRestResponse()
                        .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(message).build()))
                        .build());
    RestResponse<String> response = gcpSecretsManagerResource.updateGlobalKmsConfig(accountId, uuid, name, keyName,
        keyRing, projectId, region, true, usageRestrictionString, uploadedInputString, delegateSelector);
    verify(accountPermissionUtils, times(1)).checkIfHarnessUser(anyString());
    assertNotNull(response);
    assertEquals(response.getResponseMessages().get(0).getMessage(), message);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGlobalKmsConfigWithoutUuid() throws IOException {
    when(accountPermissionUtils.checkIfHarnessUser(anyString())).thenReturn(null);
    when(gcpSecretsManagerService.updateGcpKmsConfig(anyString(), any(), anyBoolean())).thenCallRealMethod();
    String message = "Cannot have id as empty when updating secret manager configuration";
    exceptionRule.expect(SecretManagementException.class);
    exceptionRule.expectMessage(message);
    gcpSecretsManagerResource.updateGlobalKmsConfig(accountId, null, name, keyName, keyRing, projectId, region, true,
        usageRestrictionString, uploadedInputString, delegateSelector);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateGcpSecretsManagerConfig_ShouldFailForGlobal() throws IOException {
    when(gcpSecretsManagerService.getGlobalKmsConfig())
        .thenReturn(GcpKmsConfig.builder().uuid(uuid).accountId(GLOBAL_ACCOUNT_ID).build());
    when(accountPermissionUtils.getErrorResponse(anyString())).thenCallRealMethod();
    String message = "User not allowed to update global KMS";
    RestResponse<String> response =
        gcpSecretsManagerResource.updateGcpSecretsManagerConfig(accountId, uuid, name, keyName, keyRing, projectId,
            region, EncryptionType.GCP_KMS, true, usageRestrictionString, uploadedInputString, delegateSelector);
    assertEquals(response.getResponseMessages().get(0).getMessage(), message);
  }
}
