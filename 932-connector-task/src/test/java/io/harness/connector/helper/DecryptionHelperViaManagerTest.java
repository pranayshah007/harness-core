/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static junit.framework.TestCase.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.DecryptableEntityHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestClientExecutor;
import io.harness.rule.Owner;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PL)
public class DecryptionHelperViaManagerTest extends CategoryTest {
  @Mock SecretNGManagerClient ngSecretDecryptionClient;
  @Mock NGRestClientExecutor restClientExecutor;
  @Mock DecryptableEntityHelper decryptableEntityHelper;
  @InjectMocks @Inject DecryptionHelperViaManager decryptionHelperViaManager;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testDecryptSecretSecretCreatedViaNonHarnessSecretManagerThrowsValidationError() {
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    encryptionDetails.add(
        EncryptedDataDetail.builder().encryptedData(getEncryptedRecordData(EncryptionType.AZURE_VAULT)).build());
    InvalidRequestException invalidRequestException =
        assertThrows(InvalidRequestException.class, () -> decryptionHelperViaManager.decrypt(null, encryptionDetails));
    assertEquals("Connection via Harness Platform is allowed only if secrets used to connect to the service are"
            + " saved in Harness Built-in Secret Manager. Review the following secrets to use Connection via Harness Platform : [dummyName]",
        invalidRequestException.getMessage());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testDecryptSecretSecretCreatedViaHarnessSecretManagerReturnsDecryptableEntity() throws IOException {
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    encryptionDetails.add(
        EncryptedDataDetail.builder().encryptedData(getEncryptedRecordData(EncryptionType.LOCAL)).build());
    DecryptableEntityWithEncryptionConsumers consumers = DecryptableEntityWithEncryptionConsumers.builder().build();
    DecryptableEntity decryptableEntity = mock(DecryptableEntity.class, RETURNS_DEEP_STUBS);
    when(decryptableEntityHelper.buildDecryptableEntityWithEncryptionConsumers(decryptableEntity, encryptionDetails))
        .thenReturn(consumers);
    ResponseDTO<DecryptableEntity> restResponse = ResponseDTO.newResponse(decryptableEntity);
    Response<ResponseDTO<DecryptableEntity>> response = Response.success(restResponse);
    Call<ResponseDTO<DecryptableEntity>> responseDTOCall = mock(Call.class);
    when(ngSecretDecryptionClient.decryptEncryptedDetails(consumers, "random")).thenReturn(responseDTOCall);
    when(responseDTOCall.execute()).thenReturn(response);
    decryptionHelperViaManager.decrypt(decryptableEntity, encryptionDetails);
    verify(ngSecretDecryptionClient, times(1)).decryptEncryptedDetails(consumers, "random");
  }

  private EncryptedRecordData getEncryptedRecordData(EncryptionType encryptionType) {
    return EncryptedRecordData.builder().encryptionType(encryptionType).name("dummyName").build();
  }
}