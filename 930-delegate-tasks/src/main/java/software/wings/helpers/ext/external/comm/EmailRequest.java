/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.external.comm;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SmtpCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.mail.EmailData;
import software.wings.helpers.ext.mail.SmtpConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class EmailRequest extends CollaborationProviderRequest {
  private EmailData emailData;
  private SmtpConfig smtpConfig;
  private List<EncryptedDataDetail> encryptionDetails;

  @Builder
  public EmailRequest(CommunicationType communicationType, EmailData emailData, SmtpConfig smtpConfig,
      List<EncryptedDataDetail> encryptionDetails) {
    super(communicationType);
    this.emailData = emailData;
    this.smtpConfig = smtpConfig;
    this.encryptionDetails = encryptionDetails;
  }

  public EmailRequest(CommunicationType communicationType) {
    super(communicationType);
  }

  @Override
  public CommunicationType getCommunicationType() {
    return CommunicationType.EMAIL;
  }

  @Override
  public List<String> getCriteria() {
    return Arrays.asList(smtpConfig.getHost() + ":" + smtpConfig.getPort());
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(SmtpCapability.builder()
                                         .useSSL(smtpConfig.isUseSSL())
                                         .startTLS(smtpConfig.isStartTLS())
                                         .host(smtpConfig.getHost())
                                         .port(smtpConfig.getPort())
                                         .username(smtpConfig.getUsername())
                                         .build());
  }
}
