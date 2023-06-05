/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.impl.utility;

import static io.harness.azure.utility.AzureUtils.EXECUTE_REST_CALL_MAX_ATTEMPTS;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.utility.AzureUtils;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import com.azure.core.management.AzureEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDP)
@PrepareForTest({AzureUtils.class})
public class AzureUtilsTest extends CategoryTest {
  static String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  static String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
  static String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
  static String END_CERTIFICATE = "-----END CERTIFICATE-----";
  private final String CERT_FILE_PATH = "960-api-services/src/test/resources/__files/azure/certificate.pem";
  private final String CERT_THUMBPRINT = "eW9cb9-llympj5gu54EGs8nh96U=";

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetAzureEnvironmentType() {
    assertThat(AzureUtils.getAzureEnvironment(AzureEnvironmentType.AZURE)).isEqualTo(AzureEnvironment.AZURE);
    assertThat(AzureUtils.getAzureEnvironment(AzureEnvironmentType.AZURE_US_GOVERNMENT))
        .isEqualTo(AzureEnvironment.AZURE_US_GOVERNMENT);
    assertThat(AzureUtils.getAzureEnvironment(null)).isEqualTo(AzureEnvironment.AZURE);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetCertificateThumbprintBase64Encoded() throws IOException {
    byte[] pemFileInBytes = Files.readAllBytes(Paths.get(CERT_FILE_PATH));
    assertThat(AzureUtils.getCertificateThumbprintBase64Encoded(pemFileInBytes)).isEqualTo(CERT_THUMBPRINT);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetCertificate() throws IOException {
    byte[] pemFileInBytes = Files.readAllBytes(Paths.get(CERT_FILE_PATH));
    String certWithHeaderAndFooter = AzureUtils.getCertificate(pemFileInBytes, true);
    assertThat(certWithHeaderAndFooter).startsWith(BEGIN_CERTIFICATE);
    assertThat(certWithHeaderAndFooter).endsWith(END_CERTIFICATE);
    String certWithoutHeaderAndFooter = AzureUtils.getCertificate(pemFileInBytes, false);
    assertThat(certWithoutHeaderAndFooter).doesNotStartWith(BEGIN_CERTIFICATE);
    assertThat(certWithoutHeaderAndFooter).doesNotEndWith(END_CERTIFICATE);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetPrivateKey() throws IOException {
    byte[] pemFileInBytes = Files.readAllBytes(Paths.get(CERT_FILE_PATH));
    String keyWithHeaderAndFooter = AzureUtils.getPrivateKey(pemFileInBytes, true);
    assertThat(keyWithHeaderAndFooter).startsWith(BEGIN_PRIVATE_KEY);
    assertThat(keyWithHeaderAndFooter).endsWith(END_PRIVATE_KEY);
    String keyWithoutHeaderAndFooter = AzureUtils.getPrivateKey(pemFileInBytes, false);
    assertThat(keyWithoutHeaderAndFooter).doesNotStartWith(BEGIN_PRIVATE_KEY);
    assertThat(keyWithoutHeaderAndFooter).doesNotEndWith(END_PRIVATE_KEY);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetPrivateKeyFromPEMFile() throws IOException {
    byte[] pemFileInBytes = Files.readAllBytes(Paths.get(CERT_FILE_PATH));
    AzureUtils.getPrivateKeyFromPEMFile(pemFileInBytes);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testExecuteRestCall() throws IOException {
    Map<String, String> map = Map.of("key", "value");

    Call<Map<String, String>> callRequest = PowerMockito.mock(Call.class);
    doReturn(callRequest).when(callRequest).clone();
    doReturn(Response.success(map)).when(callRequest).execute();

    Map<String, String> result = AzureUtils.executeRestCall(callRequest, null);
    assertThat(result).isEqualTo(map);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testExecuteRestCall_FailsWithError() throws IOException {
    Call<Map<String, String>> callRequest = PowerMockito.mock(Call.class);
    doReturn(callRequest).when(callRequest).clone();
    doReturn(Response.error(503, mock(ResponseBody.class))).when(callRequest).execute();

    WingsException ex = WingsException.builder().message("custom exception").build();

    assertThatThrownBy(() -> AzureUtils.executeRestCall(callRequest, ex))
        .isInstanceOf(WingsException.class)
        .hasMessage("custom exception");
    verify(callRequest, times(EXECUTE_REST_CALL_MAX_ATTEMPTS)).execute();
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testExecuteRestCall_FailsWithException() throws IOException {
    Call<Map<String, String>> callRequest = PowerMockito.mock(Call.class);
    doReturn(callRequest).when(callRequest).clone();
    doThrow(new IOException("connection error")).when(callRequest).execute();

    WingsException ex = WingsException.builder().message("custom exception").build();

    assertThatThrownBy(() -> AzureUtils.executeRestCall(callRequest, ex))
        .isInstanceOf(WingsException.class)
        .hasMessage("custom exception");
    verify(callRequest, times(EXECUTE_REST_CALL_MAX_ATTEMPTS)).execute();
  }
}
