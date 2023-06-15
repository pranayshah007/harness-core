/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.git.model.ChangeType.NONE;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.connector.CombineCcmK8sConnectorResponseDTO;
import io.harness.connector.ConnectorCatalogueResponseDTO;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorKeys;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGVaultService;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awskmsconnector.AwsKmsConnectorDTO;
import io.harness.delegate.beans.connector.awssecretmanager.AwsSecretManagerDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsConnectorDTO;
import io.harness.delegate.beans.connector.gcpsecretmanager.GcpSecretManagerConnectorDTO;
import io.harness.delegate.beans.connector.localconnector.LocalConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.git.model.ChangeType;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.beans.NameValuePairWithDefault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
@Singleton
@Slf4j
public class SecretManagerConnectorServiceImpl implements ConnectorService {
  private final ConnectorService defaultConnectorService;
  private final ConnectorRepository connectorRepository;
  private final NGVaultService ngVaultService;
  private final EnforcementClientService enforcementClientService;
  private final TemplateResourceClient templateResourceClient;
  private static final String ENVIRONMENT_VARIABLES = "environmentVariables";

  @Inject
  public SecretManagerConnectorServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService defaultConnectorService,
      ConnectorRepository connectorRepository, NGVaultService ngVaultService,
      EnforcementClientService enforcementClientService, TemplateResourceClient templateResourceClient) {
    this.defaultConnectorService = defaultConnectorService;
    this.connectorRepository = connectorRepository;
    this.ngVaultService = ngVaultService;
    this.enforcementClientService = enforcementClientService;
    this.templateResourceClient = templateResourceClient;
  }

  @Override
  public Optional<ConnectorResponseDTO> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    return defaultConnectorService.get(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
  }

  @Override
  public Optional<ConnectorResponseDTO> getByRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    return defaultConnectorService.getByRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
  }

  @Override
  public Optional<ConnectorResponseDTO> getByName(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String name, boolean isDeletedAllowed) {
    return defaultConnectorService.getByName(
        accountIdentifier, orgIdentifier, projectIdentifier, name, isDeletedAllowed);
  }

  @Override
  public Optional<ConnectorResponseDTO> getFromBranch(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String repo, String branch) {
    return defaultConnectorService.getFromBranch(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, repo, branch);
  }

  @Override
  public ConnectorResponseDTO create(@Valid ConnectorDTO connector, @AccountIdentifier String accountIdentifier) {
    return create(connector, accountIdentifier, ChangeType.ADD);
  }

  @Override
  public ConnectorResponseDTO create(
      ConnectorDTO connector, @AccountIdentifier String accountIdentifier, ChangeType gitChangeType) {
    // To support AccountSetup call we need to create Harness Default Secret Manager irrespective of LicenseType
    if (connector.getConnectorInfo() != null
        && !HARNESS_SECRET_MANAGER_IDENTIFIER.equalsIgnoreCase(connector.getConnectorInfo().getIdentifier())) {
      log.info("Creating new Secret managers Need to check License Enforcement ");
      enforcementClientService.checkAvailability(FeatureRestrictionName.SECRET_MANAGERS, accountIdentifier);
    } else {
      log.info("[AccountSetup] Creating default Secret manager");
    }
    return createSecretManagerConnector(connector, accountIdentifier, gitChangeType);
  }

  private ConnectorResponseDTO createSecretManagerConnector(
      ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType) {
    ConnectorInfoDTO connectorInfo = connector.getConnectorInfo();
    if (get(accountIdentifier, connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(),
            connectorInfo.getIdentifier())
            .isPresent()) {
      throw new DuplicateFieldException(String.format(
          "Try using different connector identifier, [%s] cannot be used", connectorInfo.getIdentifier()));
    }

    // validate the dto received
    ConnectorConfigDTO connectorConfigDTO = connectorInfo.getConnectorConfig();
    connectorConfigDTO.validate();

    ngVaultService.processAppRole(connector, null, accountIdentifier, true);

    // Check which type of token is provided
    ngVaultService.processTokenLookup(connector, accountIdentifier);

    // Validate inputs for custom secret manager
    try {
      validateCustomSecretManagerInputs(connectorConfigDTO, accountIdentifier, connectorInfo.getOrgIdentifier(),
          connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier());
    } catch (IOException ex) {
      log.error(
          "error reading templateInputs from template YAML which is used in this Custom Secret Manager {} in account {}",
          connectorInfo.getIdentifier(), accountIdentifier);
    }

    if (isDefaultSecretManager(connector.getConnectorInfo())) {
      clearDefaultFlagOfSecretManagers(accountIdentifier, connector.getConnectorInfo().getOrgIdentifier(),
          connector.getConnectorInfo().getProjectIdentifier());
    }

    return defaultConnectorService.create(connector, accountIdentifier, NONE);
  }

  private void validateCustomSecretManagerInputs(ConnectorConfigDTO connectorConfigDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String connectorIdentifier) throws IOException {
    if (connectorConfigDTO instanceof CustomSecretManagerConnectorDTO) {
      String templateRef = ((CustomSecretManagerConnectorDTO) connectorConfigDTO).getTemplate().getTemplateRef();
      List<NameValuePairWithDefault> envVariables = ((CustomSecretManagerConnectorDTO) connectorConfigDTO)
                                                        .getTemplate()
                                                        .getTemplateInputs()
                                                        .get(ENVIRONMENT_VARIABLES);
      Set<String> inputs =
          checkForDuplicateInputsAndReturnInputsLIst(envVariables, accountIdentifier, connectorIdentifier);
      String template = NGRestUtils.getResponse(templateResourceClient.getTemplateInputsYaml(
          templateIdFromRef(templateRef), accountIdentifier, orgIdentifier, projectIdentifier,
          ((CustomSecretManagerConnectorDTO) connectorConfigDTO).getTemplate().getVersionLabel(), false));
      if (template == null || StringUtils.isBlank(template)) {
        return;
      }
      JsonNode templateInputs = YamlUtils.read(template, JsonNode.class);
      ArrayNode variablesArray = (ArrayNode) templateInputs.get(ENVIRONMENT_VARIABLES);
      verifyWhetherAllTheRuntimeParametersWereProvidedWithValues(
          variablesArray, inputs, accountIdentifier, connectorIdentifier);
    }
  }

  private Set<String> checkForDuplicateInputsAndReturnInputsLIst(
      List<NameValuePairWithDefault> templateInputs, String accountIdentifier, String connectorIdentifier) {
    Set<String> inputs = new HashSet<>();
    for (NameValuePairWithDefault node : templateInputs) {
      if (inputs.contains(node.getName())) {
        log.error("Same input is given more than once for custom SM {} in account {}. Duplicated input is {}",
            connectorIdentifier, accountIdentifier, node.getName());
        throw new InvalidRequestException("Multiple values for the same input Parameter is passed. Please check.");
      }
      inputs.add(node.getName());
    }
    return inputs;
  }

  private void verifyWhetherAllTheRuntimeParametersWereProvidedWithValues(
      ArrayNode variablesArray, Set<String> inputs, String accountIdentifier, String connectorIdentifier) {
    if (!EmptyPredicate.isEmpty(variablesArray)) {
      for (JsonNode node : variablesArray) {
        String key = node.get("name").asText();
        // validate this key value is present in the incoming connector inputs
        if (!inputs.contains(key)) {
          log.error(
              "Run time inputs of templates should be passed in Custom SM {} in account {}. Runtime parameter {} is missing.",
              connectorIdentifier, accountIdentifier, key);
          throw new InvalidRequestException("Run time inputs of templates should be passed in connector.");
        }
      }
    }
  }

  private String templateIdFromRef(String templateRef) {
    String[] arrOfStr = templateRef.split("\\.");
    return (arrOfStr.length == 1) ? arrOfStr[0] : arrOfStr[1];
  }

  private boolean isDefaultSecretManager(ConnectorInfoDTO connector) {
    switch (connector.getConnectorType()) {
      case VAULT:
        return ((VaultConnectorDTO) connector.getConnectorConfig()).isDefault();
      case AZURE_KEY_VAULT:
        return ((AzureKeyVaultConnectorDTO) connector.getConnectorConfig()).isDefault();
      case GCP_KMS:
        return ((GcpKmsConnectorDTO) connector.getConnectorConfig()).isDefault();
      case AWS_KMS:
        return ((AwsKmsConnectorDTO) connector.getConnectorConfig()).isDefault();
      case AWS_SECRET_MANAGER:
        return ((AwsSecretManagerDTO) connector.getConnectorConfig()).isDefault();
      case LOCAL:
        return ((LocalConnectorDTO) connector.getConnectorConfig()).isDefault();
      case CUSTOM_SECRET_MANAGER:
        return ((CustomSecretManagerConnectorDTO) connector.getConnectorConfig()).isDefault();
      case GCP_SECRET_MANAGER:
        return ((GcpSecretManagerConnectorDTO) connector.getConnectorConfig()).isDefault();
      default:
        throw new SecretManagementException(ErrorCode.SECRET_MANAGEMENT_ERROR,
            String.format("Unsupported Secret Manager type [%s]", connector.getConnectorType()), WingsException.USER);
    }
  }

  private void clearDefaultFlagOfSecretManagers(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = Criteria.where(ConnectorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ConnectorKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ConnectorKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ConnectorKeys.categories)
                            .in(ConnectorCategory.SECRET_MANAGER)
                            .and(ConnectorKeys.deleted)
                            .ne(Boolean.TRUE)
                            .and(VaultConnectorKeys.isDefault)
                            .is(Boolean.TRUE);

    Query query = new Query(criteria);
    Update update = new Update().set(VaultConnectorKeys.isDefault, Boolean.FALSE);
    connectorRepository.updateMultiple(query, update);
  }

  @Override
  public ConnectorResponseDTO update(ConnectorDTO connector, String accountIdentifier) {
    return update(connector, accountIdentifier, ChangeType.MODIFY);
  }

  @Override
  public ConnectorResponseDTO update(ConnectorDTO connector, String accountIdentifier, ChangeType gitChangeType) {
    ConnectorInfoDTO connectorInfo = connector.getConnectorInfo();
    ConnectorConfigDTO connectorConfigDTO = connectorInfo.getConnectorConfig();

    // validate fields of dto
    connectorConfigDTO.validate();

    Optional<ConnectorResponseDTO> existingConnectorDTO = get(accountIdentifier, connectorInfo.getOrgIdentifier(),
        connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier());
    boolean alreadyDefaultSM = false;
    if (existingConnectorDTO.isPresent()) {
      ConnectorConfigDTO existingConnectorConfigDTO = existingConnectorDTO.get().getConnector().getConnectorConfig();
      ngVaultService.processAppRole(connector, existingConnectorConfigDTO, accountIdentifier, false);

      // Check which type of token is provided
      ngVaultService.processTokenLookup(connector, accountIdentifier);
      alreadyDefaultSM = isDefaultSecretManager(existingConnectorDTO.get().getConnector());
    } else {
      throw new InvalidRequestException(
          String.format("Secret Manager with identifier %s not found.", connectorInfo.getIdentifier()));
    }

    if (isDefaultSecretManager(connector.getConnectorInfo())) {
      clearDefaultFlagOfSecretManagers(accountIdentifier, connector.getConnectorInfo().getOrgIdentifier(),
          connector.getConnectorInfo().getProjectIdentifier());
    } else if (alreadyDefaultSM) {
      setHarnessSecretManagerAsDefault(accountIdentifier, connector.getConnectorInfo().getOrgIdentifier(),
          connector.getConnectorInfo().getProjectIdentifier());
    }
    return defaultConnectorService.update(connector, accountIdentifier, NONE);
  }

  private void setHarnessSecretManagerAsDefault(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = Criteria.where(ConnectorKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ConnectorKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ConnectorKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ConnectorKeys.deleted)
                            .ne(Boolean.TRUE)
                            .and(ConnectorKeys.identifier)
                            .is(HARNESS_SECRET_MANAGER_IDENTIFIER);

    Update update = new Update().set(VaultConnectorKeys.isDefault, Boolean.TRUE);
    connectorRepository.update(criteria, update, NONE, projectIdentifier, orgIdentifier, accountIdentifier);
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, boolean forceDelete) {
    return defaultConnectorService.delete(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, NONE, forceDelete);
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifier, ChangeType changeType, boolean forceDelete) {
    return defaultConnectorService.delete(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, changeType, forceDelete);
  }

  @Override
  public boolean validateTheIdentifierIsUnique(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    return defaultConnectorService.validateTheIdentifierIsUnique(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
  }

  @Override
  public ConnectorValidationResult validate(ConnectorDTO connector, String accountIdentifier) {
    throw new UnsupportedOperationException("Cannot validate secret manager, use test connection API instead");
  }

  @Override
  public ConnectorValidationResult testConnection(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifier) {
    return defaultConnectorService.testConnection(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
  }

  @Override
  public ConnectorCatalogueResponseDTO getConnectorCatalogue(String accountIdentifier) {
    return defaultConnectorService.getConnectorCatalogue(accountIdentifier);
  }

  @Override
  public void updateConnectorEntityWithPerpetualtaskId(String accountIdentifier, String connectorOrgIdentifier,
      String connectorProjectIdentifier, String connectorIdentifier, String perpetualTaskId) {
    defaultConnectorService.updateConnectorEntityWithPerpetualtaskId(
        accountIdentifier, connectorOrgIdentifier, connectorProjectIdentifier, connectorIdentifier, perpetualTaskId);
  }

  @Override
  public void updateActivityDetailsInTheConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, ConnectorValidationResult connectorValidationResult,
      Long activityTime) {
    defaultConnectorService.updateActivityDetailsInTheConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, connectorValidationResult, activityTime);
  }

  @Override
  public ConnectorValidationResult testGitRepoConnection(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, String gitRepoURL) {
    return defaultConnectorService.testGitRepoConnection(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, gitRepoURL);
  }

  @Override
  public ConnectorStatistics getConnectorStatistics(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return defaultConnectorService.getConnectorStatistics(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public String getHeartbeatPerpetualTaskId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return defaultConnectorService.getHeartbeatPerpetualTaskId(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public void resetHeartbeatForReferringConnectors(List<Pair<String, String>> connectorPerpetualTaskInfoList) {
    defaultConnectorService.resetHeartbeatForReferringConnectors(connectorPerpetualTaskInfoList);
  }

  @Override
  public void resetHeartBeatTask(String accountId, String taskId) {
    defaultConnectorService.resetHeartBeatTask(accountId, taskId);
  }

  @Override
  public Page<ConnectorResponseDTO> list(String accountIdentifier, ConnectorFilterPropertiesDTO filterProperties,
      String orgIdentifier, String projectIdentifier, String filterIdentifier, String searchTerm,
      Boolean includeAllConnectorsAccessibleAtScope, Boolean getDistinctFromBranches, Pageable pageable) {
    return defaultConnectorService.list(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier,
        filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable);
  }

  @Override
  public Page<ConnectorResponseDTO> list(String accountIdentifier, ConnectorFilterPropertiesDTO filterProperties,
      String orgIdentifier, String projectIdentifier, String filterIdentifier, String searchTerm,
      Boolean includeAllConnectorsAccessibleAtScope, Boolean getDistinctFromBranches, Pageable pageable,
      String version) {
    return defaultConnectorService.list(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier,
        filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable,
        version);
  }

  @Override
  public long count(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return defaultConnectorService.count(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public Page<ConnectorResponseDTO> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String searchTerm, ConnectorType type, ConnectorCategory category,
      ConnectorCategory sourceCategory, String version) {
    throw new UnsupportedOperationException("Cannot call list api on secret manager");
  }

  @Override
  public List<ConnectorResponseDTO> listbyFQN(String accountIdentifier, List<String> connectorFQN) {
    return defaultConnectorService.listbyFQN(accountIdentifier, connectorFQN);
  }

  @Override
  public void deleteBatch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifierList) {
    defaultConnectorService.deleteBatch(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifierList);
  }

  @Override
  public Page<CombineCcmK8sConnectorResponseDTO> listCcmK8S(String accountIdentifier,
      ConnectorFilterPropertiesDTO filterProperties, String orgIdentifier, String projectIdentifier,
      String filterIdentifier, String searchTerm, Boolean includeAllConnectorsAccessibleAtScope,
      Boolean getDistinctFromBranches, Pageable pageable) {
    return defaultConnectorService.listCcmK8S(accountIdentifier, filterProperties, orgIdentifier, projectIdentifier,
        filterIdentifier, searchTerm, includeAllConnectorsAccessibleAtScope, getDistinctFromBranches, pageable);
  }

  @Override
  public boolean markEntityInvalid(String accountIdentifier, EntityReference entityReference, String invalidYaml) {
    return defaultConnectorService.markEntityInvalid(accountIdentifier, entityReference, invalidYaml);
  }

  @Override
  public boolean checkConnectorExecutableOnDelegate(ConnectorInfoDTO connectorInfo) {
    return defaultConnectorService.checkConnectorExecutableOnDelegate(connectorInfo);
  }

  @Override
  public ConnectorDTO fullSyncEntity(EntityDetailProtoDTO entityDetailProtoDTO, boolean isFullSyncingToDefaultBranch) {
    return defaultConnectorService.fullSyncEntity(entityDetailProtoDTO, isFullSyncingToDefaultBranch);
  }

  @Override
  public ConnectorResponseDTO updateGitFilePath(
      ConnectorDTO connectorDTO, String accountIdentifier, String newFilePath) {
    return defaultConnectorService.updateGitFilePath(connectorDTO, accountIdentifier, newFilePath);
  }

  @Override
  public List<Map<String, String>> getAttributes(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> connectorIdentifiers) {
    return defaultConnectorService.getAttributes(accountId, orgIdentifier, projectIdentifier, connectorIdentifiers);
  }

  @Override
  public Long countConnectors(String accountIdentifier) {
    return defaultConnectorService.countConnectors(accountIdentifier);
  }
}
