/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.rule.OwnerRule.SOURABH;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedRecord;
import io.harness.beans.DelegateFileEncryptedRecordDataPackage;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.task.terraform.handlers.HarnessSMEncryptionDecryptionHandler;
import io.harness.managerclient.DelegateManagerEncryptionDecryptionHarnessSMClient;
import io.harness.network.SafeHttpCall;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.SecretManagerType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class HarnessSMEncryptionDecryptionHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private EncryptionConfig encryptionConfig;
  @Mock protected DelegateFileManagerBase delegateFileManager;
  @Mock private DelegateManagerEncryptionDecryptionHarnessSMClient delegateManagerEncryptionDecryptionHarnessSMClient;
  @Mock Call<RestResponse<EncryptedRecordData>> encryptedRecordCall;
  @InjectMocks HarnessSMEncryptionDecryptionHandler harnessSMEncryptionDecryptionHandler;

  public static final String ON_FILE_STORAGE = "onFileStorage";

  private String accountId;
  private final byte[] fileContent = "TerraformPlan".getBytes();
  private final char[] terraformPlan = "TerraformPlan".toCharArray();
  private final String encodedTfPlan = encodeBase64(terraformPlan);

  @Before
  public void setup() {
    when(delegateManagerEncryptionDecryptionHarnessSMClient.encryptHarnessSMSecretNG(any(), any()))
        .thenReturn(encryptedRecordCall);
    accountId = UUIDGenerator.generateUuid();
    when(encryptionConfig.getAccountId()).thenReturn(accountId);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testEncryptContent() throws IOException {
    RestResponse<EncryptedRecordData> encryptedRecordDataRestResponse = new RestResponse<>();
    encryptedRecordDataRestResponse.setResource(EncryptedRecordData.builder().encryptedValue(terraformPlan).build());
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(encryptedRecordDataRestResponse);
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    EncryptedRecord record = harnessSMEncryptionDecryptionHandler.encryptContent(fileContent, encryptionConfig);
    assertThat(record.getEncryptedValue()).isEqualTo(terraformPlan);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testEncryptFile() throws IOException {
    RestResponse<DelegateFileEncryptedRecordDataPackage> callResponse = new RestResponse<>();
    callResponse.setResource(DelegateFileEncryptedRecordDataPackage.builder()
                                 .encryptedRecordData(EncryptedRecordData.builder().build())
                                 .delegateFileId("file")
                                 .build());
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(callResponse);
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    DelegateFile delegateFile = DelegateFile.Builder.aDelegateFile().withFileId("NotFile").build();
    EncryptedRecord record =
        harnessSMEncryptionDecryptionHandler.encryptFile(fileContent, encryptionConfig, delegateFile);
    assertThat(delegateFile.getFileId()).isEqualTo("file");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testDecryptFile() throws IOException {
    RestResponse<DecryptedRecord> callResponse = new RestResponse<>();
    callResponse.setResource(
        DecryptedRecord.builder().decryptedValue(encodeBase64(terraformPlan).toCharArray()).build());
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    when(delegateFileManager.downloadByFileId(any(), any(), any()))
        .thenReturn(new ByteArrayInputStream("tfplan".getBytes(StandardCharsets.UTF_8)));
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(callResponse);
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    Map<String, Object> map = new HashMap<>();
    map.put(ON_FILE_STORAGE, TRUE);
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder()
                                                  .additionalMetadata(AdditionalMetadata.builder().values(map).build())
                                                  .encryptedValue(terraformPlan)
                                                  .build();
    harnessSMEncryptionDecryptionHandler.getDecryptedContent(encryptionConfig, encryptedRecordData, "account");
    verify(delegateFileManager, times(1)).downloadByFileId(any(), any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testDecryptFileWithoutDownloadFile() throws IOException {
    RestResponse<DecryptedRecord> callResponse = new RestResponse<>();
    callResponse.setResource(
        DecryptedRecord.builder().decryptedValue(encodeBase64(terraformPlan).toCharArray()).build());
    MockedStatic<SafeHttpCall> aStatic = Mockito.mockStatic(SafeHttpCall.class);
    when(delegateFileManager.downloadByFileId(any(), any(), any()))
        .thenReturn(new ByteArrayInputStream("tfplan".getBytes(StandardCharsets.UTF_8)));
    aStatic.when(() -> SafeHttpCall.execute(any())).thenReturn(callResponse);
    when(encryptionConfig.getType()).thenReturn(SecretManagerType.KMS);
    Map<String, Object> map = new HashMap<>();
    map.put(ON_FILE_STORAGE, FALSE);
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder()
                                                  .additionalMetadata(AdditionalMetadata.builder().values(map).build())
                                                  .encryptedValue(terraformPlan)
                                                  .build();
    harnessSMEncryptionDecryptionHandler.getDecryptedContent(encryptionConfig, encryptedRecordData, "account");
    verify(delegateFileManager, times(0)).downloadByFileId(any(), any(), any());
  }
}
