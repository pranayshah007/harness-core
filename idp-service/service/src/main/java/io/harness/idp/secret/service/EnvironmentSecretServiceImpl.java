/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.service;

import static io.harness.k8s.constants.K8sConstants.BACKSTAGE_SECRET;
import static io.harness.k8s.constants.K8sConstants.DEFAULT_NAMESPACE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.secret.beans.entity.EnvironmentSecretEntity;
import io.harness.idp.secret.mappers.EnvironmentSecretMapper;
import io.harness.idp.secret.repositories.EnvironmentSecretRepository;
import io.harness.k8s.client.K8sClient;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EnvironmentSecretServiceImpl implements EnvironmentSecretService {
  private static final String SUCCEEDED = "succeeded";
  private static final String FAILED = "failed";
  private EnvironmentSecretRepository environmentSecretRepository;
  private K8sClient k8sClient;
  @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;

  @Override
  public Optional<EnvironmentSecret> findByIdAndAccountIdentifier(String identifier, String accountIdentifier) {
    Optional<EnvironmentSecretEntity> secretOpt =
        environmentSecretRepository.findByIdAndAccountIdentifier(identifier, accountIdentifier);
    return secretOpt.map(EnvironmentSecretMapper::toDTO);
  }

  @Override
  public EnvironmentSecret saveAndSyncK8sSecret(EnvironmentSecret environmentSecret, String accountIdentifier)
      throws Exception {
    boolean success =
        syncK8sSecret(accountIdentifier, environmentSecret.getName(), environmentSecret.getSecretIdentifier());
    log.info("Secret [{}] insert {} for the account [{}]", environmentSecret.getSecretIdentifier(),
        success ? SUCCEEDED : FAILED, accountIdentifier);
    EnvironmentSecretEntity environmentSecretEntity = EnvironmentSecretMapper.fromDTO(environmentSecret);
    environmentSecretEntity.setAccountIdentifier(accountIdentifier);
    return EnvironmentSecretMapper.toDTO(environmentSecretRepository.save(environmentSecretEntity));
  }

  @Override
  public EnvironmentSecret updateAndSyncK8sSecret(EnvironmentSecret environmentSecret, String accountIdentifier)
      throws Exception {
    boolean success =
        syncK8sSecret(accountIdentifier, environmentSecret.getName(), environmentSecret.getSecretIdentifier());
    log.info("Secret [{}] update {} for the account [{}]", environmentSecret.getSecretIdentifier(),
        success ? SUCCEEDED : FAILED, accountIdentifier);
    EnvironmentSecretEntity environmentSecretEntity = EnvironmentSecretMapper.fromDTO(environmentSecret);
    environmentSecretEntity.setAccountIdentifier(accountIdentifier);
    return EnvironmentSecretMapper.toDTO(environmentSecretRepository.update(environmentSecretEntity));
  }

  @Override
  public List<EnvironmentSecret> findByAccountIdentifier(String accountIdentifier) {
    List<EnvironmentSecretEntity> secrets = environmentSecretRepository.findByAccountIdentifier(accountIdentifier);
    List<EnvironmentSecret> secretDTOs = new ArrayList<>();
    secrets.forEach(environmentSecretEntity -> secretDTOs.add(EnvironmentSecretMapper.toDTO(environmentSecretEntity)));
    return secretDTOs;
  }

  @Override
  public void delete(String secretIdentifier, String harnessAccount) {
    EnvironmentSecretEntity environmentSecretEntity = EnvironmentSecretEntity.builder().id(secretIdentifier).build();
    environmentSecretRepository.delete(environmentSecretEntity);
  }

  @Override
  public void processSecretUpdate(EntityChangeDTO entityChangeDTO, String action) throws Exception {
    String secretIdentifier = entityChangeDTO.getIdentifier().getValue();
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    Optional<EnvironmentSecretEntity> envSecretOpt =
        environmentSecretRepository.findByAccountIdentifierAndSecretIdentifier(accountIdentifier, secretIdentifier);
    if (envSecretOpt.isPresent()) {
      boolean success =
          syncK8sSecret(accountIdentifier, envSecretOpt.get().getName(), envSecretOpt.get().getSecretIdentifier());
      log.info("Secret [{}] update {} in the namespace [{}]", secretIdentifier, success ? "succeeded" : "failed",
          DEFAULT_NAMESPACE);
    } else {
      // TODO: There might be too many secrets overall. We might have to consider removing this log line in future
      log.info("Secret {} is not tracker by IDP, hence not processing it", secretIdentifier);
    }
  }

  private boolean syncK8sSecret(String accountIdentifier, String environmentName, String secretIdentifier)
      throws Exception {
    // TODO: get the namespace for the given account. Currently assuming it to be default. Needs to be fixed.
    Map<String, byte[]> secretData = new HashMap<>();
    DecryptedSecretValue decryptedValue =
        ngSecretService.getDecryptedSecretValue(accountIdentifier, null, null, secretIdentifier);
    secretData.put(environmentName, decryptedValue.getDecryptedValue().getBytes());
    return k8sClient.updateSecretData(DEFAULT_NAMESPACE, BACKSTAGE_SECRET, secretData, false);
  }
}
