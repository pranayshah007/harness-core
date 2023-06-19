/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.beans.DecryptedRecord;
import software.wings.beans.EncryptedRecord;
import software.wings.beans.EncryptedSMData;
import software.wings.service.impl.DelegateManagerEncryptionDecryptionHarnessSMServiceNGImpl;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit.http.Body;

@Api("/encryption-ng")
@Path("/encryption-ng")
@Produces("application/json")
@Slf4j
@OwnedBy(CDP)
public class DelegateManagerEncryptionDecryptionHarnessSMResourceNG {
  @Inject DelegateManagerEncryptionDecryptionHarnessSMServiceNGImpl encryptionDecryptionService;

  @DelegateAuth
  @POST
  @Path("/encrypt-harness-sm-secret")
  public RestResponse<EncryptedRecordData> encryptHarnessSMSecretNG(
      @NotNull @QueryParam("accountId") String accountId, @NotNull EncryptedRecord encryptedRecord) {
    try {
      return new RestResponse<>(encryptionDecryptionService.encryptDataNG(accountId, encryptedRecord.getContent()));
    } catch (Exception e) {
      log.error(format("Unable to encrypt the content for harness secret manager for account %s", accountId), e);
      throw e;
    }
  }

  @DelegateAuth
  @POST
  @Path("/decrypt-harness-sm-secret")
  public RestResponse<DecryptedRecord> decryptHarnessSMSecretNG(
      @NotNull @QueryParam("accountId") String accountId, @NotNull @Body EncryptedSMData encryptedSMData) {
    try {
      return new RestResponse<>(
          encryptionDecryptionService.decryptDataNG(accountId, encryptedSMData.toEncryptedRecordData()));
    } catch (Exception e) {
      log.error(format("Unable to decrypt the content for harness secret manager for account %s", accountId), e);
      throw e;
    }
  }
}
