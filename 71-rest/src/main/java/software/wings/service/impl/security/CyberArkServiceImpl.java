package software.wings.service.impl.security;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.CYBERARK_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.persistence.HPersistence.upToOne;
import static io.harness.threading.Morpheus.sleep;
import static java.time.Duration.ofMillis;
import static software.wings.beans.Application.GLOBAL_APP_ID;
import static software.wings.service.intfc.security.SecretManagementDelegateService.NUM_OF_RETRIES;
import static software.wings.service.intfc.security.SecretManager.ACCOUNT_ID_KEY;
import static software.wings.service.intfc.security.SecretManager.SECRET_NAME_KEY;
import static software.wings.settings.SettingValue.SettingVariableTypes.CYBERARK;

import com.google.inject.Singleton;

import com.mongodb.DuplicateKeyException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoUtils;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import software.wings.beans.CyberArkConfig;
import software.wings.beans.CyberArkConfig.CyberArkConfigKeys;
import software.wings.beans.SyncTaskContext;
import software.wings.security.encryption.EncryptedData;
import software.wings.security.encryption.EncryptedData.EncryptedDataKeys;
import software.wings.security.encryption.EncryptedDataParent;
import software.wings.service.intfc.security.CyberArkService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import java.time.Duration;
import java.util.Objects;

/**
 * @author marklu on 2019-08-01
 */
@Singleton
@Slf4j
public class CyberArkServiceImpl extends AbstractSecretServiceImpl implements CyberArkService {
  private static final String CLIENT_CERTIFICATE_NAME_SUFFIX = "_clientCertificate";

  @Override
  public char[] decrypt(EncryptedData data, String accountId, CyberArkConfig cyberArkConfig) {
    int failedAttempts = 0;
    while (true) {
      try {
        SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                              .accountId(accountId)
                                              .timeout(Duration.ofSeconds(5).toMillis())
                                              .appId(GLOBAL_APP_ID)
                                              .correlationId(data.getName())
                                              .build();
        return delegateProxyFactory.get(SecretManagementDelegateService.class, syncTaskContext)
            .decrypt(data, cyberArkConfig);
      } catch (WingsException e) {
        failedAttempts++;
        logger.info("AWS Secrets Manager decryption failed for encryptedData {}. trial num: {}", data.getName(),
            failedAttempts, e);
        if (failedAttempts == NUM_OF_RETRIES) {
          throw e;
        }
        sleep(ofMillis(1000));
      }
    }
  }

  @Override
  public CyberArkConfig getConfig(String accountId, String configId) {
    CyberArkConfig cyberArkConfig = wingsPersistence.createQuery(CyberArkConfig.class)
                                        .filter(ACCOUNT_ID_KEY, accountId)
                                        .filter(ID_KEY, configId)
                                        .get();
    if (cyberArkConfig != null) {
      decryptCyberArkConfigSecrets(accountId, cyberArkConfig, false);
    }

    return cyberArkConfig;
  }

  @Override
  public String saveConfig(String accountId, CyberArkConfig cyberArkConfig) {
    cyberArkConfig.setAccountId(accountId);
    checkIfSecretsManagerConfigCanBeCreatedOrUpdated(accountId);

    CyberArkConfig oldConfigForAudit = null;
    CyberArkConfig savedConfig = null;
    boolean credentialChanged = true;
    if (!isEmpty(cyberArkConfig.getUuid())) {
      savedConfig = getConfig(accountId, cyberArkConfig.getUuid());
      if (SECRET_MASK.equals(cyberArkConfig.getClientCertificate())) {
        cyberArkConfig.setClientCertificate(savedConfig.getClientCertificate());
      }
      credentialChanged =
          (!SECRET_MASK.equals(cyberArkConfig.getClientCertificate())
              && !Objects.equals(cyberArkConfig.getClientCertificate(), savedConfig.getClientCertificate()))
          || !Objects.equals(cyberArkConfig.getCyberArkUrl(), savedConfig.getCyberArkUrl())
          || !Objects.equals(cyberArkConfig.getAppId(), savedConfig.getAppId());

      // Secret field un-decrypted version of saved config
      savedConfig = wingsPersistence.get(CyberArkConfig.class, cyberArkConfig.getUuid());
      oldConfigForAudit = KryoUtils.clone(savedConfig);
    }

    // Validate every time when secret manager config change submitted
    validateConfig(cyberArkConfig);

    if (!credentialChanged) {
      // update without client certificate or url changes
      savedConfig.setName(cyberArkConfig.getName());
      savedConfig.setDefault(cyberArkConfig.isDefault());

      // PL-3237: Audit secret manager config changes.
      generateAuditForSecretManager(accountId, oldConfigForAudit, savedConfig);

      return secretManagerConfigService.save(savedConfig);
    }

    EncryptedData clientCertEncryptedData =
        getEncryptedDataForClientCertificateField(savedConfig, cyberArkConfig, cyberArkConfig.getClientCertificate());

    cyberArkConfig.setClientCertificate(null);
    String secretsManagerConfigId;
    try {
      secretsManagerConfigId = secretManagerConfigService.save(cyberArkConfig);
    } catch (DuplicateKeyException e) {
      String message = "Another CyberArk secret manager with the same name or URL exists";
      throw new SecretManagementException(CYBERARK_OPERATION_ERROR, message, USER_SRE);
    }

    // Create a LOCAL encrypted record for AWS secret key
    String clientCertEncryptedDataId = saveClientCertificateField(cyberArkConfig, secretsManagerConfigId,
        clientCertEncryptedData, CLIENT_CERTIFICATE_NAME_SUFFIX, CyberArkConfigKeys.clientCertificate);
    cyberArkConfig.setClientCertificate(clientCertEncryptedDataId);

    // PL-3237: Audit secret manager config changes.
    generateAuditForSecretManager(accountId, oldConfigForAudit, cyberArkConfig);

    return secretManagerConfigService.save(cyberArkConfig);
  }

  private EncryptedData getEncryptedDataForClientCertificateField(
      CyberArkConfig savedConfig, CyberArkConfig cyberArkConfig, String clientCertificate) {
    EncryptedData encryptedData = isNotEmpty(clientCertificate) && !Objects.equals(SECRET_MASK, clientCertificate)
        ? encryptLocal(clientCertificate.toCharArray())
        : null;

    EncryptedData savedEncryptedData = null;
    if (savedConfig != null) {
      // Get by auth token encrypted record by Id or name.
      Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class);
      query.criteria(ACCOUNT_ID_KEY)
          .equal(cyberArkConfig.getAccountId())
          .or(query.criteria(ID_KEY).equal(savedConfig.getClientCertificate()),
              query.criteria(SECRET_NAME_KEY).equal(cyberArkConfig.getName() + CLIENT_CERTIFICATE_NAME_SUFFIX));
      savedEncryptedData = query.get();
    }
    if (savedEncryptedData != null && encryptedData != null) {
      savedEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
      savedEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
      return savedEncryptedData;
    } else {
      return encryptedData;
    }
  }

  private String saveClientCertificateField(CyberArkConfig cyberArkConfig, String configId,
      EncryptedData secretFieldEncryptedData, String clientCertNameSuffix, String fieldName) {
    String secretFieldEncryptedDataId = null;
    if (secretFieldEncryptedData != null) {
      secretFieldEncryptedData.setAccountId(cyberArkConfig.getAccountId());
      secretFieldEncryptedData.addParent(
          EncryptedDataParent.createParentRef(configId, CyberArkConfig.class, fieldName, CYBERARK));
      secretFieldEncryptedData.setType(CYBERARK);
      secretFieldEncryptedData.setName(cyberArkConfig.getName() + clientCertNameSuffix);
      secretFieldEncryptedDataId = wingsPersistence.save(secretFieldEncryptedData);
    }
    return secretFieldEncryptedDataId;
  }

  @Override
  public boolean deleteConfig(String accountId, String configId) {
    long count = wingsPersistence.createQuery(EncryptedData.class)
                     .filter(ACCOUNT_ID_KEY, accountId)
                     .filter(EncryptedDataKeys.kmsId, configId)
                     .filter(EncryptedDataKeys.encryptionType, EncryptionType.CYBERARK)
                     .count(upToOne);

    if (count > 0) {
      String message = "Cannot delete the CyberArk configuration since there are secrets encrypted with it. "
          + "Please transition your secrets to another secret manager and try again.";
      throw new SecretManagementException(CYBERARK_OPERATION_ERROR, message, USER);
    }

    CyberArkConfig cyberArkConfig = wingsPersistence.get(CyberArkConfig.class, configId);
    checkNotNull(cyberArkConfig, "No CyberArk secret manager configuration found with id " + configId);

    if (isNotEmpty(cyberArkConfig.getClientCertificate())) {
      wingsPersistence.delete(EncryptedData.class, cyberArkConfig.getClientCertificate());
      logger.info("Deleted encrypted auth token record {} associated with CyberArk Secrets Manager '{}'",
          cyberArkConfig.getClientCertificate(), cyberArkConfig.getName());
    }

    return deleteSecretManagerAndGenerateAudit(accountId, cyberArkConfig);
  }

  @Override
  public void validateConfig(CyberArkConfig cyberArkConfig) {
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(cyberArkConfig.getAccountId())
                                          .timeout(Duration.ofSeconds(10).toMillis())
                                          .appId(GLOBAL_APP_ID)
                                          .correlationId(cyberArkConfig.getUuid())
                                          .build();
    delegateProxyFactory.get(SecretManagementDelegateService.class, syncTaskContext)
        .validateCyberArkConfig(cyberArkConfig);
  }

  @Override
  public void decryptCyberArkConfigSecrets(String accountId, CyberArkConfig cyberArkConfig, boolean maskSecret) {
    EncryptedData encryptedClientCert =
        wingsPersistence.get(EncryptedData.class, cyberArkConfig.getClientCertificate());
    if (encryptedClientCert != null) {
      if (maskSecret) {
        cyberArkConfig.maskSecrets();
      } else {
        cyberArkConfig.setClientCertificate(String.valueOf(decryptLocal(encryptedClientCert)));
      }
    }
  }
}
