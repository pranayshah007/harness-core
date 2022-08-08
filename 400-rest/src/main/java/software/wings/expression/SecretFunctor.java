/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.expression.SecretManagerFunctorInterface.obtainExpression;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.exception.FunctorException;
import io.harness.expression.LateBindingMap;
import io.harness.expression.SecretString;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.ServiceVariable;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import java.util.List;
import lombok.Builder;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Builder
public class SecretFunctor extends LateBindingMap {
  public enum Mode {
    ALTERNATING,
    CASCADING,
  }
  private Mode mode;
  private ManagerDecryptionService managerDecryptionService;
  private SecretManager secretManager;
  private String accountId;
  private String appId;
  private String envId;
  private boolean adoptDelegateDecryption;
  private int expressionFunctorToken;

  private boolean disablePhasing;

  public Object getValue(String secretName) {
    if (adoptDelegateDecryption && !disablePhasing) {
      return obtainExpression(secretName, expressionFunctorToken);
    }

    EncryptedData encryptedData = null;
    if (mode == null || mode == Mode.ALTERNATING) {
      encryptedData = appId == null || GLOBAL_APP_ID.equals(appId)
          ? secretManager.getSecretMappedToAccountByName(accountId, secretName)
          : secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretName);
    } else if (mode == Mode.CASCADING) {
      encryptedData = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretName);
      if (encryptedData == null) {
        encryptedData = secretManager.getSecretMappedToAccountByName(accountId, secretName);
      }
    }
    if (encryptedData == null) {
      throw new FunctorException("No secret found with name [" + secretName
          + "]. Either the secret is being attempted to be used out of its scope or the secret does not exist.");
    }
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(accountId)
                                          .type(ENCRYPTED_TEXT)
                                          .encryptedValue(encryptedData.getUuid())
                                          .secretTextName(secretName)
                                          .build();
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(serviceVariable, null, null);
    managerDecryptionService.decrypt(serviceVariable, encryptionDetails);
    return SecretString.builder().value(new String(serviceVariable.getValue())).build();
  }

  @Override
  public Object get(Object key) {
    return getValue((String) key);
  }
}
