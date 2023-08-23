/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.reflection.ReflectionUtils.getFieldByName;
import static io.harness.security.SimpleEncryption.CHARSET;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.ci.beans.entities.EncryptedDataDetails;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;
import io.harness.utils.IdentifierRefHelper;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class HostedVmSecretManagerFunctor implements ExpressionFunctor {
  private long expressionFunctorToken;
  private NGAccess ngAccess;
  private Cache<String, EncryptedDataDetails> secretsCache;
  private SecretManagerClientService ngSecretService;

  @Builder.Default Map<String, String> evaluatedSecrets = new HashMap<>();

  public Object obtain(String secretIdentifier, int token) {
    if (evaluatedSecrets.containsKey(secretIdentifier)) {
      return evaluatedSecrets.get(secretIdentifier);
    }

    IdentifierRef secretIdentifierRef = IdentifierRefHelper.getIdentifierRef(secretIdentifier,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    SecretVariableDTO secretVariableDTO = SecretVariableDTO.builder()
                                              .name(secretIdentifierRef.getIdentifier())
                                              .secret(SecretRefData.builder()
                                                          .identifier(secretIdentifierRef.getIdentifier())
                                                          .scope(secretIdentifierRef.getScope())
                                                          .build())
                                              .type(SecretVariableDTO.Type.TEXT)
                                              .build();

    int keyHash = SecretsCacheKey.builder()
                      .accountIdentifier(ngAccess.getAccountIdentifier())
                      .orgIdentifier(ngAccess.getOrgIdentifier())
                      .projectIdentifier(ngAccess.getProjectIdentifier())
                      .secretVariableDTO(secretVariableDTO)
                      .build()
                      .hashCode();

    List<EncryptedDataDetail> encryptedDataDetails = null;
    if (secretsCache != null) {
      EncryptedDataDetails cachedValue = secretsCache.get(String.valueOf(keyHash));
      if (cachedValue != null && isNotEmpty(cachedValue.getEncryptedDataDetailList())) {
        // Cache hit.
        encryptedDataDetails = cachedValue.getEncryptedDataDetailList();
        log.info("Decrypted secret {} from cached value", secretIdentifier);
      }
    }

    if (isEmpty(encryptedDataDetails)) {
      // Cache miss.
      encryptedDataDetails = ngSecretService.getEncryptionDetails(ngAccess, secretVariableDTO);

      if (EmptyPredicate.isEmpty(encryptedDataDetails)) {
        throw new InvalidRequestException("No secret found with identifier + [" + secretIdentifier + "]", USER);
      }

      // Skip caching secrets for HashiCorp vault.
      List<EncryptedDataDetail> encryptedDataDetailsToCache =
          encryptedDataDetails.stream()
              .filter(encryptedDataDetail
                  -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.VAULT)
              .collect(Collectors.toList());
      if (isNotEmpty(encryptedDataDetailsToCache)) {
        secretsCache.put(String.valueOf(keyHash),
            EncryptedDataDetails.builder().encryptedDataDetailList(encryptedDataDetailsToCache).build());
      }
      log.info("Decrypted secret {} from ng secret service", secretIdentifier);
    }

    List<EncryptedDataDetail> localEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (isNotEmpty(localEncryptedDetails)) {
      decryptLocal(secretVariableDTO, localEncryptedDetails);
      final String secretValue = new String(secretVariableDTO.getSecret().getDecryptedValue());
      evaluatedSecrets.put(secretIdentifier, secretValue);
      return secretValue;
    }

    throw new InvalidRequestException("Non harness secret manager used for [" + secretIdentifier + "]", USER);
  }

  private void decryptLocal(DecryptableEntity decryptableEntity, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isEmpty(encryptedDataDetails)) {
      return;
    }

    encryptedDataDetails.stream()
        .filter(
            encryptedDataDetail -> encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
        .forEach(encryptedDataDetail -> {
          SimpleEncryption encryption = new SimpleEncryption(encryptedDataDetail.getEncryptedData().getEncryptionKey());
          char[] decryptChars = encryption.decryptChars(encryptedDataDetail.getEncryptedData().getEncryptedValue());
          if (encryptedDataDetail.getEncryptedData().isBase64Encoded()) {
            byte[] decodedBytes = EncodingUtils.decodeBase64(decryptChars);
            decryptChars = CHARSET.decode(ByteBuffer.wrap(decodedBytes)).array();
          }

          Field f = getFieldByName(decryptableEntity.getClass(), encryptedDataDetail.getFieldName());

          if (f != null) {
            f.setAccessible(true);
            try {
              SecretRefData secretRefData = (SecretRefData) f.get(decryptableEntity);
              secretRefData.setDecryptedValue(decryptChars);
            } catch (IllegalAccessException e) {
              throw new InvalidRequestException("Decryption failed for  " + encryptedDataDetail.toString(), e);
            }
          }
        });
  }
}

@Builder
@EqualsAndHashCode
class SecretsCacheKey {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  SecretVariableDTO secretVariableDTO;
}
