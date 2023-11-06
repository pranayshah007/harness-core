/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import static io.harness.idp.common.Constants.GITHUB_APP_PRIVATE_KEY_REF;
import static io.harness.idp.common.Constants.LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES;
import static io.harness.idp.common.Constants.PRIVATE_KEY_END;
import static io.harness.idp.common.Constants.PRIVATE_KEY_START;
import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;

import static java.lang.String.format;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.FeatureName;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.encryption.EncryptionUtils;
import io.harness.idp.envvariable.beans.entity.BackstageEnvConfigVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.remote.client.CGRestUtils;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariable;
import io.harness.spec.server.idp.v1.model.ResolvedEnvVariableResponse;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class BackstageEnvVariableServiceImpl implements BackstageEnvVariableService {
  private static final String UNSUPPORTED_SECRET_MANAGER_MESSAGE =
      "Invalid request: Decryption is supported only for secrets encrypted via harness managed secret managers";
  private static final String SECRET_NOT_FOUND_PATTERN =
      "Invalid request: Secret with identifier .* does not exist in this scope";
  public static final Pattern ERROR_PATTERN =
      Pattern.compile(UNSUPPORTED_SECRET_MANAGER_MESSAGE + "|" + SECRET_NOT_FOUND_PATTERN);
  private final BackstageEnvVariableRepository backstageEnvVariableRepository;
  private final K8sClient k8sClient;
  private final SecretManagerClientService ngSecretService;
  private final NamespaceService namespaceService;
  private final Map<BackstageEnvVariableType, BackstageEnvVariableMapper> envVariableMap;
  private final SetupUsageProducer setupUsageProducer;
  private final AccountClient accountClient;
  private final String idpEncryptionSecret;
  private static final Gson gson = new Gson();

  @Inject
  public BackstageEnvVariableServiceImpl(BackstageEnvVariableRepository backstageEnvVariableRepository,
      K8sClient k8sClient, @Named("PRIVILEGED") SecretManagerClientService ngSecretService,
      NamespaceService namespaceService, Map<BackstageEnvVariableType, BackstageEnvVariableMapper> envVariableMap,
      SetupUsageProducer setupUsageProducer, AccountClient accountClient,
      @Named("idpEncryptionSecret") String idpEncryptionSecret) {
    this.backstageEnvVariableRepository = backstageEnvVariableRepository;
    this.k8sClient = k8sClient;
    this.ngSecretService = ngSecretService;
    this.namespaceService = namespaceService;
    this.envVariableMap = envVariableMap;
    this.setupUsageProducer = setupUsageProducer;
    this.accountClient = accountClient;
    this.idpEncryptionSecret = idpEncryptionSecret;
  }

  @Override
  public Optional<BackstageEnvVariable> findByIdAndAccountIdentifier(String identifier, String accountIdentifier) {
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByIdAndAccountIdentifier(identifier, accountIdentifier);
    if (envVariableEntityOpt.isEmpty()) {
      return Optional.empty();
    }
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
    return Optional.of(envVariableMapper.toDto(envVariableEntityOpt.get()));
  }

  @Override
  public Optional<BackstageEnvVariable> findByEnvNameAndAccountIdentifier(String envName, String accountIdentifier) {
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
    if (envVariableEntityOpt.isEmpty()) {
      return Optional.empty();
    }
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
    return Optional.of(envVariableMapper.toDto(envVariableEntityOpt.get()));
  }

  @Deprecated(forRemoval = true)
  @Override
  public BackstageEnvVariable create(BackstageEnvVariable envVariable, String accountIdentifier) {
    envVariable = removeAccountFromIdentifierForBackstageEnvVariable(envVariable);
    sync(Collections.singletonList(envVariable), accountIdentifier);
    BackstageEnvVariableMapper envVariableMapper =
        getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
    long secretLastModifiedAt = getSecretLastModifiedAt(envVariable, accountIdentifier);
    BackstageEnvVariableEntity backstageEnvVariableEntity =
        envVariableMapper.fromDto(envVariable, accountIdentifier, secretLastModifiedAt);
    BackstageEnvVariable responseEnvVariable =
        envVariableMapper.toDto(backstageEnvVariableRepository.save(backstageEnvVariableEntity));

    setupUsageProducer.publishEnvVariableSetupUsage(Collections.singletonList(responseEnvVariable), accountIdentifier);

    return responseEnvVariable;
  }

  List<BackstageEnvVariable> createMulti(List<BackstageEnvVariable> requestEnvVariables, String accountIdentifier) {
    requestEnvVariables = removeAccountFromIdentifierForBackstageEnvVarList(requestEnvVariables);
    sync(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariableEntity> entities = getEntitiesFromDtos(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariable> responseEnvVariables = new ArrayList<>();
    backstageEnvVariableRepository.saveAll(entities).forEach(envVariableEntity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper(envVariableEntity.getType());
      responseEnvVariables.add(envVariableMapper.toDto(envVariableEntity));
    });

    setupUsageProducer.publishEnvVariableSetupUsage(responseEnvVariables, accountIdentifier);

    return responseEnvVariables;
  }

  @Deprecated(forRemoval = true)
  @Override
  public BackstageEnvVariable update(BackstageEnvVariable envVariable, String accountIdentifier) {
    envVariable = removeAccountFromIdentifierForBackstageEnvVariable(envVariable);
    sync(Collections.singletonList(envVariable), accountIdentifier);
    BackstageEnvVariableMapper envVariableMapper =
        getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
    long secretLastModifiedAt = getSecretLastModifiedAt(envVariable, accountIdentifier);
    BackstageEnvVariableEntity backstageEnvVariableEntity =
        envVariableMapper.fromDto(envVariable, accountIdentifier, secretLastModifiedAt);
    backstageEnvVariableEntity.setAccountIdentifier(accountIdentifier);
    BackstageEnvVariable responseVariable =
        envVariableMapper.toDto(backstageEnvVariableRepository.update(backstageEnvVariableEntity));

    List<BackstageEnvVariable> responseList = Collections.singletonList(responseVariable);
    setupUsageProducer.deleteEnvVariableSetupUsage(responseList, accountIdentifier);
    setupUsageProducer.publishEnvVariableSetupUsage(responseList, accountIdentifier);

    return responseVariable;
  }

  List<BackstageEnvVariable> updateMulti(List<BackstageEnvVariable> requestEnvVariables, String accountIdentifier) {
    requestEnvVariables = removeAccountFromIdentifierForBackstageEnvVarList(requestEnvVariables);
    sync(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariableEntity> entities = getEntitiesFromDtos(requestEnvVariables, accountIdentifier);
    List<BackstageEnvVariable> responseVariables = new ArrayList<>();
    entities.forEach(entity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((entity.getType()));
      responseVariables.add(envVariableMapper.toDto(backstageEnvVariableRepository.update(entity)));
    });

    setupUsageProducer.deleteEnvVariableSetupUsage(responseVariables, accountIdentifier);
    setupUsageProducer.publishEnvVariableSetupUsage(responseVariables, accountIdentifier);

    return responseVariables;
  }

  @Override
  public List<BackstageEnvVariable> createOrUpdate(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    List<String> envNamesFromRequest =
        envVariables.stream().map(BackstageEnvVariable::getEnvName).collect(Collectors.toList());
    Set<String> envNamesFromDB =
        backstageEnvVariableRepository
            .findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNamesFromRequest)
            .stream()
            .map(BackstageEnvVariableEntity::getEnvName)
            .collect(Collectors.toSet());
    List<BackstageEnvVariable> envVariablesToUpdate = new ArrayList<>();
    List<BackstageEnvVariable> envVariablesToAdd = new ArrayList<>();
    envVariables.forEach(envVariable -> {
      if (envNamesFromDB.contains(envVariable.getEnvName())) {
        envVariablesToUpdate.add(envVariable);
      } else {
        envVariablesToAdd.add(envVariable);
      }
    });
    List<BackstageEnvVariable> response = createMulti(envVariablesToAdd, accountIdentifier);
    response.addAll(updateMulti(envVariablesToUpdate, accountIdentifier));
    return response;
  }

  @Override
  public List<BackstageEnvVariable> findByAccountIdentifier(String accountIdentifier) {
    List<BackstageEnvVariableEntity> entities =
        backstageEnvVariableRepository.findByAccountIdentifier(accountIdentifier);
    List<BackstageEnvVariable> secretDTOs = new ArrayList<>();
    entities.forEach(entity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((entity.getType()));
      secretDTOs.add(envVariableMapper.toDto(entity));
    });
    return secretDTOs;
  }

  @Deprecated(forRemoval = true)
  @Override
  public void delete(String identifier, String accountIdentifier) {
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByIdAndAccountIdentifier(identifier, accountIdentifier);
    if (envVariableEntityOpt.isEmpty()) {
      throw new InvalidRequestException(
          format("Environment variable [%s] not found in account [%s]", identifier, accountIdentifier));
    }
    BackstageEnvVariableEntity envVariableEntity = envVariableEntityOpt.get();
    k8sClient.removeSecretData(getNamespaceForAccount(accountIdentifier), BACKSTAGE_SECRET,
        Collections.singletonList(envVariableEntity.getEnvName()));
    backstageEnvVariableRepository.delete(envVariableEntity);
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntity.getType()));

    setupUsageProducer.deleteEnvVariableSetupUsage(
        Collections.singletonList(envVariableMapper.toDto(envVariableEntity)), accountIdentifier);
  }

  @Override
  public void findAndSync(String accountIdentifier) {
    List<BackstageEnvVariable> variables = findByAccountIdentifier(accountIdentifier);
    createOrUpdate(variables, accountIdentifier);
  }

  @Override
  public void deleteMulti(List<String> secretIdentifiers, String accountIdentifier) {
    Iterable<BackstageEnvVariableEntity> envVariableEntities =
        backstageEnvVariableRepository.findAllById(secretIdentifiers);
    List<String> envNames = new ArrayList<>();
    List<BackstageEnvVariable> deletedVariables = new ArrayList<>();
    envVariableEntities.forEach(envVariableEntity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntity.getType()));
      deletedVariables.add(envVariableMapper.toDto(envVariableEntity));
      envNames.add(envVariableEntity.getEnvName());
    });
    k8sClient.removeSecretData(getNamespaceForAccount(accountIdentifier), BACKSTAGE_SECRET, envNames);
    backstageEnvVariableRepository.deleteAllById(secretIdentifiers);

    setupUsageProducer.deleteEnvVariableSetupUsage(deletedVariables, accountIdentifier);
  }

  @Override
  public void deleteMultiUsingEnvNames(List<String> envNames, String accountIdentifier) {
    // removing from setup usages
    Iterable<BackstageEnvVariableEntity> envVariableEntities =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNames);
    List<BackstageEnvVariable> deletedVariables = new ArrayList<>();
    envVariableEntities.forEach(envVariableEntity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntity.getType()));
      deletedVariables.add(envVariableMapper.toDto(envVariableEntity));
    });
    setupUsageProducer.deleteEnvVariableSetupUsage(deletedVariables, accountIdentifier);

    k8sClient.removeSecretData(getNamespaceForAccount(accountIdentifier), BACKSTAGE_SECRET, envNames);
    backstageEnvVariableRepository.deleteAllByAccountIdentifierAndEnvNames(accountIdentifier, envNames);
  }

  @Override
  public void processSecretUpdate(EntityChangeDTO entityChangeDTO) {
    String secretIdentifier = entityChangeDTO.getIdentifier().getValue();
    secretIdentifier = CommonUtils.removeAccountFromIdentifier(secretIdentifier);
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    Optional<BackstageEnvVariableEntity> envVariableEntityOpt =
        backstageEnvVariableRepository.findByAccountIdentifierAndHarnessSecretIdentifier(
            accountIdentifier, secretIdentifier);
    if (envVariableEntityOpt.isPresent()) {
      log.info("Secret {} is used by backstage env variable {}. Processing secret update for account {}",
          secretIdentifier, envVariableEntityOpt.get().getEnvName(), accountIdentifier);
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
      sync(Collections.singletonList(envVariableMapper.toDto(envVariableEntityOpt.get())), accountIdentifier);
    }
  }

  @Override
  public void processSecretDelete(EntityChangeDTO entityChangeDTO) {
    String secretIdentifier = entityChangeDTO.getIdentifier().getValue();
    secretIdentifier = CommonUtils.removeAccountFromIdentifier(secretIdentifier);
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    Optional<BackstageEnvVariableEntity> backstageEnvVariableOpt =
        backstageEnvVariableRepository.updateSecretIsDeleted(accountIdentifier, secretIdentifier, true);
    if (backstageEnvVariableOpt.isPresent()) {
      log.info("Marking backstage env variable {} as deleted as it uses deleted secret {} for account {}",
          backstageEnvVariableOpt.get().getEnvName(), secretIdentifier, accountIdentifier);
    }
  }

  public void sync(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    if (envVariables.isEmpty()) {
      return;
    }
    boolean loadSecretsDynamically = CGRestUtils.getResponse(
        accountClient.isFeatureFlagEnabled(FeatureName.IDP_DYNAMIC_SECRET_RESOLUTION.name(), accountIdentifier));
    log.info("IDP_DYNAMIC_SECRET_RESOLUTION FF enabled: {} for account {}", loadSecretsDynamically, accountIdentifier);

    envVariables = removeAccountFromIdentifierForBackstageEnvVarList(envVariables);
    Map<String, byte[]> secretData = new HashMap<>();

    Set<String> envNames = envVariables.stream().map(BackstageEnvVariable::getEnvName).collect(Collectors.toSet());
    log.info("Checking if envs {} need to be added/updated for account {}", envNames, accountIdentifier);

    Map<String, BackstageEnvVariableEntity> entitiesMap =
        backstageEnvVariableRepository
            .findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, new ArrayList<>(envNames))
            .stream()
            .collect(Collectors.toMap(BackstageEnvVariableEntity::getEnvName, Function.identity()));

    for (BackstageEnvVariable envVariable : envVariables) {
      BackstageEnvVariableEntity entity = entitiesMap.get(envVariable.getEnvName());
      if (envVariable.getType().name().equals(BackstageEnvVariableType.SECRET.name())) {
        handleSecretEnv(accountIdentifier, entity, envVariable, secretData, loadSecretsDynamically);
      } else {
        handleConfigEnv(accountIdentifier, entity, envVariable, secretData);
      }
    }
    String namespace = getNamespaceForAccount(accountIdentifier);
    k8sClient.updateSecretData(namespace, BACKSTAGE_SECRET, secretData);
    log.info("Successfully updated secret {} in the namespace {}", BACKSTAGE_SECRET, namespace);
  }

  private void handleSecretEnv(String accountIdentifier, BackstageEnvVariableEntity entity,
      BackstageEnvVariable envVariable, Map<String, byte[]> secretData, boolean loadSecretsDynamically) {
    BackstageEnvSecretVariable secretEnvVariable = (BackstageEnvSecretVariable) envVariable;
    Pair<String, Long> decryptedValueAndLastModifiedAt = getDecryptedValueAndLastModifiedTime(
        secretEnvVariable.getEnvName(), secretEnvVariable.getHarnessSecretIdentifier(), accountIdentifier);
    String decryptedValue = decryptedValueAndLastModifiedAt.getFirst();
    Long lastModifiedAt = decryptedValueAndLastModifiedAt.getSecond();
    BackstageEnvSecretVariableEntity secretEntity = (BackstageEnvSecretVariableEntity) entity;
    if (secretEntity == null // create scenario
        || !secretEntity.getHarnessSecretIdentifier().equals(
            secretEnvVariable.getHarnessSecretIdentifier()) // different secret scenario
        || lastModifiedAt == 0 // old ng-manager scenario
        || secretEntity.getSecretLastModifiedAt() < lastModifiedAt) { // secret update scenario
      if (loadSecretsDynamically) {
        log.info("Updating LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES for env {} for account {}",
            envVariable.getEnvName(), accountIdentifier);
        secretData.put(LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES, String.valueOf(System.currentTimeMillis()).getBytes());
      } else {
        log.info("Adding/Updating secret env {} for account {}", envVariable.getEnvName(), accountIdentifier);
        secretData.put(envVariable.getEnvName(), decryptedValue.getBytes());
      }
    }
  }

  private void handleConfigEnv(String accountIdentifier, BackstageEnvVariableEntity entity,
      BackstageEnvVariable envVariable, Map<String, byte[]> secretData) {
    BackstageEnvConfigVariable configEnvVariable = (BackstageEnvConfigVariable) envVariable;
    if (entity == null
        || !((BackstageEnvConfigVariableEntity) entity).getValue().equals(configEnvVariable.getValue())) {
      log.info("Adding/Updating config env {} for account {}", envVariable.getEnvName(), accountIdentifier);
      secretData.put(envVariable.getEnvName(), ((BackstageEnvConfigVariable) envVariable).getValue().getBytes());
    }
  }

  @Override
  public List<BackstageEnvSecretVariable> getAllSecretIdentifierForMultipleEnvVariablesInAccount(
      String accountIdentifier, List<String> envVariableNames) {
    List<BackstageEnvSecretVariable> resultList = new ArrayList<>();
    List<BackstageEnvVariableEntity> listEnvVariablesAndSecretId =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
            accountIdentifier, envVariableNames);
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((BackstageEnvVariableType.SECRET));

    for (BackstageEnvVariableEntity backstageEnvVariableEntity : listEnvVariablesAndSecretId) {
      resultList.add((BackstageEnvSecretVariable) envVariableMapper.toDto(backstageEnvVariableEntity));
    }
    return resultList;
  }

  @Override
  public List<BackstageEnvVariable> findByEnvNamesAndAccountIdentifier(
      List<String> envNames, String accountIdentifier) {
    List<BackstageEnvVariableEntity> entities =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(accountIdentifier, envNames);
    List<BackstageEnvVariable> backstageEnvVariables = new ArrayList<>();
    entities.forEach(entity -> {
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((entity.getType()));
      backstageEnvVariables.add(envVariableMapper.toDto(entity));
    });
    return backstageEnvVariables;
  }

  @Override
  public ResolvedEnvVariableResponse resolveSecrets(String accountIdentifier, String namespace) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    if (!namespaceInfo.getNamespace().equals(namespace)) {
      throw new InvalidRequestException(
          String.format("The request namespace [%s] does not match with the account namespace [%s] for account [%s]",
              namespace, namespaceInfo.getNamespace(), accountIdentifier));
    }

    List<BackstageEnvVariableEntity> entities =
        backstageEnvVariableRepository.findByAccountIdentifier(accountIdentifier);
    List<ResolvedEnvVariable> resolvedEnvVariables = new ArrayList<>();
    for (BackstageEnvVariableEntity entity : entities) {
      if (entity.getType().equals(BackstageEnvVariableType.CONFIG)) {
        continue;
      }
      BackstageEnvSecretVariableEntity secretVariableEntity = ((BackstageEnvSecretVariableEntity) entity);
      ResolvedEnvVariable resolvedEnv = new ResolvedEnvVariable();
      resolvedEnv.setEnvName(secretVariableEntity.getEnvName());
      String decryptedValue = "";
      try {
        decryptedValue = getDecryptedValueAndLastModifiedTime(
            secretVariableEntity.getEnvName(), secretVariableEntity.getHarnessSecretIdentifier(), accountIdentifier)
                             .getFirst();
      } catch (UnexpectedException ex) {
        log.error("Skipping secret resolution as resolution failed with error", ex);
      }
      resolvedEnv.setDecryptedValue(decryptedValue);
      resolvedEnvVariables.add(resolvedEnv);
    }
    String json = gson.toJson(resolvedEnvVariables);
    return BackstageEnvVariableMapper.toResolvedVariableResponse(
        EncryptionUtils.encryptString(json, idpEncryptionSecret));
  }

  private String getNamespaceForAccount(String accountIdentifier) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(accountIdentifier);
    return namespaceInfo.getNamespace();
  }

  private BackstageEnvVariableMapper getEnvVariableMapper(BackstageEnvVariableType envVariableType) {
    BackstageEnvVariableMapper envVariableMapper = envVariableMap.get(envVariableType);
    if (envVariableMapper == null) {
      throw new InvalidRequestException("Backstage env variable type not set");
    }
    return envVariableMapper;
  }

  private List<BackstageEnvVariableEntity> getEntitiesFromDtos(
      List<BackstageEnvVariable> requestEnvVariables, String accountIdentifier) {
    return requestEnvVariables.stream()
        .map(envVariable -> {
          BackstageEnvVariableMapper envVariableMapper =
              getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
          long secretLastModifiedAt = getSecretLastModifiedAt(envVariable, accountIdentifier);
          return envVariableMapper.fromDto(envVariable, accountIdentifier, secretLastModifiedAt);
        })
        .collect(Collectors.toList());
  }

  private List<BackstageEnvVariable> removeAccountFromIdentifierForBackstageEnvVarList(
      List<BackstageEnvVariable> backstageEnvVariableList) {
    List<BackstageEnvVariable> returnList = new ArrayList<>();
    for (BackstageEnvVariable backstageEnvVariable : backstageEnvVariableList) {
      returnList.add(removeAccountFromIdentifierForBackstageEnvVariable(backstageEnvVariable));
    }
    return returnList;
  }

  private BackstageEnvVariable removeAccountFromIdentifierForBackstageEnvVariable(
      BackstageEnvVariable backstageEnvVariable) {
    if (backstageEnvVariable.getType().name().equals(BackstageEnvVariableType.SECRET.name())) {
      BackstageEnvSecretVariable backstageEnvSecretVariable = (BackstageEnvSecretVariable) backstageEnvVariable;
      backstageEnvSecretVariable.setHarnessSecretIdentifier(
          CommonUtils.removeAccountFromIdentifier(backstageEnvSecretVariable.getHarnessSecretIdentifier()));
      return backstageEnvSecretVariable;
    }
    return backstageEnvVariable;
  }
  @Override
  public Pair<String, Long> getDecryptedValueAndLastModifiedTime(
      String envName, String secretIdentifier, String accountIdentifier) {
    int maxRetries = 3;
    int baseDelayMillis = 1000;
    int retryAttempts = 0;
    String exceptionMessage = "";

    while (retryAttempts < maxRetries) {
      try {
        DecryptedSecretValue decryptedValue =
            ngSecretService.getDecryptedSecretValue(accountIdentifier, null, null, secretIdentifier);

        if (envName.equals(GITHUB_APP_PRIVATE_KEY_REF)) {
          SecretResponseWrapper secretResponseWrapper =
              ngSecretService.getSecret(accountIdentifier, null, null, secretIdentifier);
          if (secretResponseWrapper.getSecret().getType().equals(SecretType.SecretFile)) {
            decryptedValue.setDecryptedValue(
                new String(Base64.getDecoder().decode(decryptedValue.getDecryptedValue()), StandardCharsets.UTF_8));
          }
          if (secretResponseWrapper.getSecret().getType().equals(SecretType.SecretText)) {
            String privateKeyFormatted = formatPrivateKey(decryptedValue.getDecryptedValue());
            decryptedValue.setDecryptedValue(privateKeyFormatted);
          }
        }
        return new Pair<>(decryptedValue.getDecryptedValue(), decryptedValue.getLastModifiedAt());
      } catch (Exception e) {
        exceptionMessage = e.getMessage();
        Matcher matcher = ERROR_PATTERN.matcher(exceptionMessage);
        if (matcher.find()) {
          break;
        }
        log.warn("Error while decrypting secret {} for account {}. Retry: {}, Error: {}", secretIdentifier,
            accountIdentifier, retryAttempts + 1, e.getMessage());

        int delayMillis = (int) (baseDelayMillis * Math.pow(2, retryAttempts));

        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }

        retryAttempts++;
      }
    }

    throw new UnexpectedException(String.format(
        "%s. Env %s, Secret %s, Account %s", exceptionMessage, envName, secretIdentifier, accountIdentifier));
  }

  @Override
  public void reloadSecrets(String harnessAccount, String namespace) {
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceForAccountIdentifier(harnessAccount);
    if (!namespaceInfo.getNamespace().equals(namespace)) {
      throw new InvalidRequestException(
          String.format("The request namespace [%s] does not match with the account namespace [%s] for account [%s]",
              namespace, namespaceInfo.getNamespace(), harnessAccount));
    }
    Map<String, byte[]> secretData = new HashMap<>();
    secretData.put(LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES, String.valueOf(System.currentTimeMillis()).getBytes());
    k8sClient.updateSecretData(namespace, BACKSTAGE_SECRET, secretData);
    log.info("Successfully updated LAST_UPDATED_TIMESTAMP_FOR_ENV_VARIABLES in secret {} in the namespace {}",
        BACKSTAGE_SECRET, namespace);
  }

  private String formatPrivateKey(String privateKey) {
    privateKey = privateKey.replace(PRIVATE_KEY_START + " ", "");
    privateKey = privateKey.replace(PRIVATE_KEY_END, "");
    privateKey = privateKey.replace(" ", "\n");
    String privateKeyFormatted = PRIVATE_KEY_START + "\n";
    privateKeyFormatted = privateKeyFormatted + privateKey;
    privateKeyFormatted = privateKeyFormatted + PRIVATE_KEY_END;
    return privateKeyFormatted;
  }

  private long getSecretLastModifiedAt(BackstageEnvVariable envVariable, String accountIdentifier) {
    if (envVariable.getType().equals(BackstageEnvVariable.TypeEnum.CONFIG)) {
      return 0L;
    }
    BackstageEnvSecretVariable secret = (BackstageEnvSecretVariable) envVariable;
    return getDecryptedValueAndLastModifiedTime(
        secret.getEnvName(), secret.getHarnessSecretIdentifier(), accountIdentifier)
        .getSecond();
  }
}
