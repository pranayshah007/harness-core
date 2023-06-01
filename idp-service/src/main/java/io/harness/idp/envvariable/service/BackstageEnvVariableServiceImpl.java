/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import static io.harness.idp.common.Constants.GITHUB_APP_PRIVATE_KEY_REF;
import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class BackstageEnvVariableServiceImpl implements BackstageEnvVariableService {
  private BackstageEnvVariableRepository backstageEnvVariableRepository;
  private K8sClient k8sClient;
  @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  private NamespaceService namespaceService;
  private Map<BackstageEnvVariableType, BackstageEnvVariableMapper> envVariableMap;
  private SetupUsageProducer setupUsageProducer;

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

  @Override
  public BackstageEnvVariable create(BackstageEnvVariable envVariable, String accountIdentifier) {
    envVariable = removeAccountFromIdentifierForBackstageEnvVariable(envVariable);
    sync(Collections.singletonList(envVariable), accountIdentifier);
    BackstageEnvVariableMapper envVariableMapper =
        getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
    BackstageEnvVariableEntity backstageEnvVariableEntity = envVariableMapper.fromDto(envVariable, accountIdentifier);
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

  @Override
  public BackstageEnvVariable update(BackstageEnvVariable envVariable, String accountIdentifier) {
    envVariable = removeAccountFromIdentifierForBackstageEnvVariable(envVariable);
    sync(Collections.singletonList(envVariable), accountIdentifier);
    BackstageEnvVariableMapper envVariableMapper =
        getEnvVariableMapper(BackstageEnvVariableType.valueOf(envVariable.getType().name()));
    BackstageEnvVariableEntity backstageEnvVariableEntity = envVariableMapper.fromDto(envVariable, accountIdentifier);
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
    sync(variables, accountIdentifier);
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
      BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((envVariableEntityOpt.get().getType()));
      sync(Collections.singletonList(envVariableMapper.toDto(envVariableEntityOpt.get())), accountIdentifier);
    } else {
      // TODO: There might be too many secrets overall. We might have to consider removing this log line in future
      log.info("Secret {} is not tracker by IDP, hence not processing it", secretIdentifier);
    }
  }

  public void sync(List<BackstageEnvVariable> envVariables, String accountIdentifier) {
    if (envVariables.isEmpty()) {
      return;
    }
    envVariables = removeAccountFromIdentifierForBackstageEnvVarList(envVariables);
    Map<String, byte[]> secretData = new HashMap<>();
    for (BackstageEnvVariable envVariable : envVariables) {
      String envName = envVariable.getEnvName();

      if (envVariable.getType().name().equals(BackstageEnvVariableType.SECRET.name())) {
        String secretIdentifier = ((BackstageEnvSecretVariable) envVariable).getHarnessSecretIdentifier();
        DecryptedSecretValue decryptedValue =
            ngSecretService.getDecryptedSecretValue(accountIdentifier, null, null, secretIdentifier);

        if (envName.equals(GITHUB_APP_PRIVATE_KEY_REF)) {
          decryptedValue.setDecryptedValue(new String(Base64.getDecoder().decode(decryptedValue.getDecryptedValue())));
        }

        secretData.put(envName, decryptedValue.getDecryptedValue().getBytes());
      } else {
        secretData.put(envName, ((BackstageEnvConfigVariable) envVariable).getValue().getBytes());
      }
    }
    String namespace = getNamespaceForAccount(accountIdentifier);
    k8sClient.updateSecretData(namespace, BACKSTAGE_SECRET, secretData, false);
    log.info("Successfully updated secret {} in the namespace {}", BACKSTAGE_SECRET, namespace);
  }

  @Override
  public List<BackstageEnvSecretVariable> getAllSecretIdentifierForMultipleEnvVariablesInAccount(
      String accountIdentifier, List<String> envVariableNames) {
    List<BackstageEnvSecretVariable> resultList = new ArrayList<>();
    List<BackstageEnvVariableEntity> listEnvVariablesAndSecretId =
        backstageEnvVariableRepository.findAllByAccountIdentifierAndMultipleEnvNames(
            accountIdentifier, envVariableNames);
    BackstageEnvVariableMapper envVariableMapper = getEnvVariableMapper((BackstageEnvVariableType.SECRET));
    List<BackstageEnvSecretVariable> backstageEnvSecretVariableList = new ArrayList<>();

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
          return envVariableMapper.fromDto(envVariable, accountIdentifier);
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
}
