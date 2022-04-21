package io.harness.encryptors;

import static io.harness.rule.OwnerRule.TEJAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.clients.AzureBlobEncryptor;
import io.harness.helpers.ext.azure.BlobClientAuthenticator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AzureBlobConfig;

import com.azure.storage.blob.BlobClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@Slf4j
@RunWith(PowerMockRunner.class)
@PrepareForTest({BlobClientAuthenticator.class, BlobClient.class})
@PowerMockIgnore({"javax.security.*", "org.apache.http.conn.ssl.", "javax.net.ssl.", "javax.crypto.*", "sun.*"})
public class AzureBlobEncryptorTest extends CategoryTest {
  private AzureBlobEncryptor azureBlobEncryptor;
  private AzureBlobConfig azureBlobConfig;
  private BlobClient blobClient;
  private String blobName = "dummy";

  @Before
  public void setup() {
    azureBlobEncryptor = new AzureBlobEncryptor(HTimeLimiter.create());
    azureBlobConfig = AzureBlobConfig.builder()
                          .uuid(UUIDGenerator.generateUuid())
                          .name(UUIDGenerator.generateUuid())
                          .accountId(UUIDGenerator.generateUuid())
                          .clientId("dummy")
                          .secretKey(UUIDGenerator.generateUuid())
                          .tenantId("dummy")
                          .containerURL(UUIDGenerator.generateUuid())
                          .azureEnvironmentType(AzureEnvironmentType.AZURE)
                          .encryptionType(EncryptionType.AZURE_BLOB)
                          .isDefault(false)
                          .build();
    mockStatic(BlobClientAuthenticator.class);
    blobClient = PowerMockito.mock(BlobClient.class);
    when(BlobClientAuthenticator.getClient(any(), any())).thenReturn(blobClient);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateSecret() {
    String plainText = UUIDGenerator.generateUuid();
    ArgumentCaptor<ByteArrayInputStream> byteArrayInputStreamArgumentCaptor =
        ArgumentCaptor.forClass(ByteArrayInputStream.class);
    ArgumentCaptor<Long> longArgumentCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    EncryptedRecord encryptedRecord =
        azureBlobEncryptor.createSecret(azureBlobConfig.getAccountId(), blobName, plainText, azureBlobConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(blobName);
    verify(blobClient, times(1))
        .upload(byteArrayInputStreamArgumentCaptor.capture(), longArgumentCaptor.capture(),
            booleanArgumentCaptor.capture());
    int length = plainText.length();
    byte[] textArray = new byte[length];
    byteArrayInputStreamArgumentCaptor.getValue().read(textArray, 0, length);
    assertThat(textArray).isEqualTo(plainText.getBytes(StandardCharsets.UTF_8));
    assertThat(longArgumentCaptor.getValue()).isEqualTo(plainText.length());
    assertThat(booleanArgumentCaptor.getValue()).isEqualTo(false);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteSecret() {
    EncryptedRecord existingRecord = mock(EncryptedRecord.class);
    when(existingRecord.getEncryptionKey()).thenReturn(blobName.concat(Boolean.TRUE.toString()));
    Boolean deleted = azureBlobEncryptor.deleteSecret(azureBlobConfig.getAccountId(), existingRecord, azureBlobConfig);
    verify(blobClient, times(1)).delete();
    assertThat(deleted).isEqualTo(Boolean.TRUE);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdateSecret_withDelete() {
    String plainText = UUIDGenerator.generateUuid();
    EncryptedRecord existingRecord = mock(EncryptedRecord.class);
    when(existingRecord.getEncryptionKey()).thenReturn(blobName.concat(Boolean.TRUE.toString()));
    ArgumentCaptor<ByteArrayInputStream> byteArrayInputStreamArgumentCaptor =
        ArgumentCaptor.forClass(ByteArrayInputStream.class);
    ArgumentCaptor<Long> longArgumentCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    EncryptedRecord encryptedRecord = azureBlobEncryptor.updateSecret(
        azureBlobConfig.getAccountId(), blobName, plainText, existingRecord, azureBlobConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(blobName);
    verify(blobClient, times(1))
        .upload(byteArrayInputStreamArgumentCaptor.capture(), longArgumentCaptor.capture(),
            booleanArgumentCaptor.capture());
    int length = plainText.length();
    byte[] textArray = new byte[length];
    byteArrayInputStreamArgumentCaptor.getValue().read(textArray, 0, length);
    assertThat(textArray).isEqualTo(plainText.getBytes(StandardCharsets.UTF_8));
    assertThat(longArgumentCaptor.getValue()).isEqualTo(plainText.length());
    assertThat(booleanArgumentCaptor.getValue()).isEqualTo(true);
    verify(blobClient, times(1)).delete();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdateSecret_withoutDelete() {
    String plainText = UUIDGenerator.generateUuid();
    EncryptedRecord existingRecord = mock(EncryptedRecord.class);
    when(existingRecord.getEncryptionKey()).thenReturn(blobName);
    ArgumentCaptor<ByteArrayInputStream> byteArrayInputStreamArgumentCaptor =
        ArgumentCaptor.forClass(ByteArrayInputStream.class);
    ArgumentCaptor<Long> longArgumentCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    EncryptedRecord encryptedRecord = azureBlobEncryptor.updateSecret(
        azureBlobConfig.getAccountId(), blobName, plainText, existingRecord, azureBlobConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(blobName);
    verify(blobClient, times(1))
        .upload(byteArrayInputStreamArgumentCaptor.capture(), longArgumentCaptor.capture(),
            booleanArgumentCaptor.capture());
    int length = plainText.length();
    byte[] textArray = new byte[length];
    byteArrayInputStreamArgumentCaptor.getValue().read(textArray, 0, length);
    assertThat(textArray).isEqualTo(plainText.getBytes(StandardCharsets.UTF_8));
    assertThat(longArgumentCaptor.getValue()).isEqualTo(plainText.length());
    assertThat(booleanArgumentCaptor.getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testRenameSecret() {
    EncryptedRecord existingRecord = mock(EncryptedRecord.class);
    ArgumentCaptor<ByteArrayInputStream> byteArrayInputStreamArgumentCaptor =
        ArgumentCaptor.forClass(ByteArrayInputStream.class);
    ArgumentCaptor<Long> longArgumentCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Boolean> booleanArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
    when(existingRecord.getEncryptionKey()).thenReturn(blobName.concat(Boolean.TRUE.toString()));
    EncryptedRecord encryptedRecord =
        azureBlobEncryptor.renameSecret(azureBlobConfig.getAccountId(), blobName, existingRecord, azureBlobConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(blobName);
    verify(blobClient, times(1)).download(any(ByteArrayOutputStream.class));
    verify(blobClient, times(1))
        .upload(byteArrayInputStreamArgumentCaptor.capture(), longArgumentCaptor.capture(),
            booleanArgumentCaptor.capture());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testFetchSecretValue() {
    EncryptedRecord encryptedRecord = mock(EncryptedRecord.class);
    when(encryptedRecord.getEncryptionKey()).thenReturn(blobName.concat(Boolean.TRUE.toString()));
    char[] fetchedRecord =
        azureBlobEncryptor.fetchSecretValue(azureBlobConfig.getAccountId(), encryptedRecord, azureBlobConfig);
    assertThat(fetchedRecord).isNotNull();
    verify(blobClient, times(1)).download(any(ByteArrayOutputStream.class));
  }
}