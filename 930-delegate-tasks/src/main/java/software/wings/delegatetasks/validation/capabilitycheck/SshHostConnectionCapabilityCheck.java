/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.utils.PhysicalDataCenterUtils.extractHostnameFromHost;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cdng.secrets.tasks.SshSessionConfigHelper;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.CapabilityResponse.CapabilityResponseBuilder;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SshConnectivityExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.delegate.task.executioncapability.SocketConnectivityCapabilityCheck;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionFactory;
import io.harness.shell.ssh.SshClientManager;

import com.google.inject.Inject;
import com.jcraft.jsch.Session;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SshHostConnectionCapabilityCheck implements CapabilityCheck {
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    SshConnectivityExecutionCapability capability = (SshConnectivityExecutionCapability) delegateCapability;
    CapabilityResponseBuilder capabilityResponseBuilder = CapabilityResponse.builder().delegateCapability(capability);

    SshInfraDelegateConfig sshInfraDelegateConfig = capability.getSshInfraDelegateConfig();

    int port = sshInfraDelegateConfig.getSshKeySpecDto().getPort();
    String host =
        extractHostnameFromHost(capability.getHost())
            .orElseThrow(()
                             -> new InvalidArgumentsException(
                                 format("Failed to extract host name, host: %s, port: %s", capability.getHost(), port),
                                 USER_SRE));

    SSHAuthDTO authDTO = sshInfraDelegateConfig.getSshKeySpecDto().getAuth();
    if (authDTO.getAuthScheme() == SSHAuthScheme.Kerberos) {
      // connect with Kerberos to ensure it is configured correctly on delegate
      KerberosConfigDTO kerberosConfigDTO = (KerberosConfigDTO) authDTO.getSpec();
      SshSessionConfig config = generateSshSessionConfigForKerberos(
          authDTO, host, kerberosConfigDTO, sshInfraDelegateConfig.getEncryptionDataDetails(), port);
      log.info("Validating ssh Session to Host: {}, Port: {}", config.getHost(), config.getPort());

      try {
        connect(config);
        capabilityResponseBuilder.validated(true);
      } catch (Exception e) {
        log.info("Exception in SshSession Connection: ", ExceptionMessageSanitizer.sanitizeException(e));
        capabilityResponseBuilder.validated(false);
      }
    } else {
      // just check socket connectivity
      capabilityResponseBuilder.validated(SocketConnectivityCapabilityCheck.connectableHost(host, port));
    }

    return capabilityResponseBuilder.build();
  }

  void connect(SshSessionConfig config) throws Exception {
    if (config.isUseSshClient() || config.isVaultSSH()) {
      SshClientManager.test(config);
    } else {
      Session session = SshSessionFactory.getSSHSession(config);
      session.disconnect();
    }
  }

  private SshSessionConfig generateSshSessionConfigForKerberos(SSHAuthDTO authDTO, String host,
      KerberosConfigDTO kerberosConfigDTO, List<EncryptedDataDetail> encryptionDetails, int port) {
    SshSessionConfig.Builder builder =
        aSshSessionConfig().withHost(host).withPort(port).withSshConnectionTimeout(30000);
    SshSessionConfigHelper.generateKerberosBuilder(
        authDTO, kerberosConfigDTO, builder, encryptionDetails, secretDecryptionService);
    return builder.build();
  }
}
