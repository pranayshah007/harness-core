/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.notification.SmtpConfig.SmtpConfigBuilder;

import io.harness.exception.InvalidRequestException;
import io.harness.notification.remote.SmtpConfigResponse;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.mail.SmtpConfig;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.EmailHelperUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("/ng/smtp-config")
@Path("/ng/smtp-config")
@NextGenManagerAuth
public class SmtpConfigResourceNG {
  @Inject EmailHelperUtils emailHelperUtils;

  @Inject private SecretManager secretManager;

  @GET
  @Produces("application/x-kryo")
  @Consumes("application/x-kryo")
  public RestResponse<SmtpConfigResponse> getSmtpConfig(@QueryParam("accountId") String accountId) throws Exception {
    SmtpConfig smtpConfig = emailHelperUtils.getSmtpConfig(accountId, true);
    if (smtpConfig == null) {
      throw new InvalidRequestException("SMTP Configuration Missing for Account: " + accountId);
    }
    SmtpConfigBuilder notificationSmtpConfigBuilder = io.harness.notification.SmtpConfig.builder()
                                                          .host(smtpConfig.getHost())
                                                          .port(smtpConfig.getPort())
                                                          .fromAddress(smtpConfig.getFromAddress())
                                                          .useSSL(smtpConfig.isUseSSL())
                                                          .startTLS(smtpConfig.isStartTLS())
                                                          .username(smtpConfig.getUsername())
                                                          .password(smtpConfig.getPassword())
                                                          .encryptedPassword(smtpConfig.getEncryptedPassword());
    if (isNotEmpty(smtpConfig.getDelegateSelectors())) {
      notificationSmtpConfigBuilder.delegateSelectors(smtpConfig.getDelegateSelectors());
    }
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(smtpConfig);
    return new RestResponse<>(new SmtpConfigResponse(notificationSmtpConfigBuilder.build(), encryptionDetails));
  }
}
