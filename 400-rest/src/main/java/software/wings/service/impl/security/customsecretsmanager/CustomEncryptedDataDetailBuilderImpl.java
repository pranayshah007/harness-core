/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.customsecretsmanager;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.common.EncryptedDataParams;

import software.wings.expression.SecretFunctor;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerShellScript;
import software.wings.service.intfc.security.CustomEncryptedDataDetailBuilder;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class CustomEncryptedDataDetailBuilderImpl implements CustomEncryptedDataDetailBuilder {
  private final CustomSecretsManagerConnectorHelper customSecretsManagerConnectorHelper;
  private final SecretManager secretManager;
  private final ManagerDecryptionService managerDecryptionService;
  private final ExpressionEvaluator expressionEvaluator;

  @Inject
  public CustomEncryptedDataDetailBuilderImpl(CustomSecretsManagerConnectorHelper customSecretsManagerConnectorHelper,
      SecretManager secretManager, ManagerDecryptionService managerDecryptionService,
      ExpressionEvaluator expressionEvaluator) {
    this.customSecretsManagerConnectorHelper = customSecretsManagerConnectorHelper;
    this.secretManager = secretManager;
    this.managerDecryptionService = managerDecryptionService;
    this.expressionEvaluator = expressionEvaluator;
  }

  public EncryptedDataDetail buildEncryptedDataDetail(
      EncryptedData encryptedData, CustomSecretsManagerConfig customSecretsManagerConfig) {
    CustomSecretsManagerValidationUtils.validateVariables(customSecretsManagerConfig, encryptedData.getParameters());

    CustomSecretsManagerShellScript templatizedShellScript =
        customSecretsManagerConfig.getCustomSecretsManagerShellScript();
    String scriptContent = resolveVariables(customSecretsManagerConfig.getAccountId(),
        templatizedShellScript.getScriptString(), encryptedData.getParameters());
    CustomSecretsManagerShellScript resolvedShellScript = CustomSecretsManagerShellScript.builder()
                                                              .scriptString(scriptContent)
                                                              .scriptType(templatizedShellScript.getScriptType())
                                                              .timeoutMillis(templatizedShellScript.getTimeoutMillis())
                                                              .variables(templatizedShellScript.getVariables())
                                                              .build();
    customSecretsManagerConfig.setCustomSecretsManagerShellScript(resolvedShellScript);

    if (!customSecretsManagerConfig.isExecuteOnDelegate()) {
      if (customSecretsManagerConfig.isConnectorTemplatized()) {
        customSecretsManagerConnectorHelper.setConnectorInConfig(
            customSecretsManagerConfig, encryptedData.getParameters());
      }
      managerDecryptionService.decrypt(customSecretsManagerConfig.getRemoteHostConnector(),
          secretManager.getEncryptionDetails(customSecretsManagerConfig.getRemoteHostConnector()));
    }
    EncryptedRecordData encryptedRecordData = SecretManager.buildRecordData(encryptedData);

    return EncryptedDataDetail.builder()
        .encryptedData(encryptedRecordData)
        .encryptionConfig(customSecretsManagerConfig)
        .build();
  }

  private String resolveVariables(String accountId, String script, Set<EncryptedDataParams> parameters) {
    Map<String, Object> context = new HashMap<>();
    parameters.forEach(secretVariable -> context.put(secretVariable.getName(), secretVariable.getValue()));
    String scriptWithResolvedVariables = expressionEvaluator.substitute(script, context);
    context.put("secrets",
        SecretFunctor.builder()
            .managerDecryptionService(managerDecryptionService)
            .secretManager(secretManager)
            .accountId(accountId)
            .build());
    return expressionEvaluator.substitute(scriptWithResolvedVariables, context);
  }
}
