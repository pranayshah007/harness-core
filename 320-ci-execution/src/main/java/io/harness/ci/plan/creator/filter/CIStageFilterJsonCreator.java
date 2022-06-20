/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.filter;

import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.KUBERNETES_DIRECT;
import static io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type.VM;
import static io.harness.filters.FilterCreatorHelper.convertToEntityDetailProtoDTO;
import static io.harness.git.GitClientHelper.getGitRepo;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI_CODE_BASE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PROPERTIES;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.integrationstage.K8InitializeTaskUtils;
import io.harness.ci.plan.creator.filter.CIFilter.CIFilterBuilder;
import io.harness.ci.utils.ValidationUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.filters.FilterCreatorHelper;
import io.harness.filters.GenericStageFilterJsonCreator;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.util.InfrastructureUtils;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIStageFilterJsonCreator extends GenericStageFilterJsonCreator {
  @Inject ConnectorUtils connectorUtils;
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject K8InitializeTaskUtils k8InitializeTaskUtils;
  @Inject ValidationUtils validationUtils;

  @Override
  public Set<String> getSupportedStageTypes() {
    return ImmutableSet.of(StepSpecTypeConstants.CI_STAGE);
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, StageElementConfig stageElementConfig) {
    log.info("Received filter creation request for integration stage {}", stageElementConfig.getIdentifier());
    String accountId = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();

    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountId)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();
    YamlField variablesField =
        filterCreationContext.getCurrentField().getNode().getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      FilterCreatorHelper.checkIfVariableNamesAreValid(variablesField);
    }

    CIFilterBuilder ciFilterBuilder = CIFilter.builder();
    CodeBase ciCodeBase = null;
    try {
      YamlNode properties =
          YamlUtils.getGivenYamlNodeFromParentPath(filterCreationContext.getCurrentField().getNode(), PROPERTIES);
      YamlNode ciCodeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
      ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseNode);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }

    if (ciCodeBase != null) {
      if (ciCodeBase.getRepoName().getValue() != null) {
        ciFilterBuilder.repoName(ciCodeBase.getRepoName().getValue());
      } else if (ciCodeBase.getConnectorRef().getValue() != null) {
        try {
          ConnectorDetails connectorDetails =
              connectorUtils.getConnectorDetails(baseNGAccess, ciCodeBase.getConnectorRef().getValue());
          String repoName = getGitRepo(connectorUtils.retrieveURL(connectorDetails));
          ciFilterBuilder.repoName(repoName);
        } catch (Exception exception) {
          log.warn("Failed to retrieve repo");
        }
      }
    }

    validateStage(stageElementConfig);

    log.info("Successfully created filter for integration stage {}", stageElementConfig.getIdentifier());
    return ciFilterBuilder.build();
  }

  private void validateStage(StageElementConfig stageElementConfig) {
    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageElementConfig.getStageType();

    Infrastructure infrastructure = integrationStageConfig.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Infrastructure is mandatory for execution");
    }
    if (infrastructure.getType() == Infrastructure.Type.VM) {
      validationUtils.validateVmInfraDependencies(integrationStageConfig.getServiceDependencies().getValue());
    }
    if (infrastructure.getType() == KUBERNETES_DIRECT
        && k8InitializeTaskUtils.getOS(infrastructure) == OSType.Windows) {
      validationUtils.validateWindowsK8Stage(integrationStageConfig.getExecution());
    }
  }

  public Set<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, StageElementConfig stageElementConfig) {
    CodeBase ciCodeBase = null;
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();

    try {
      YamlNode properties =
          YamlUtils.getGivenYamlNodeFromParentPath(filterCreationContext.getCurrentField().getNode(), PROPERTIES);
      YamlNode ciCodeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
      ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseNode);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }

    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (ciCodeBase != null) {
      String fullQualifiedDomainName =
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
          + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + ciCodeBase.getConnectorRef();

      result.add(convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
          fullQualifiedDomainName, ciCodeBase.getConnectorRef(), EntityTypeProtoEnum.CONNECTORS));
    }

    IntegrationStageConfig integrationStage = (IntegrationStageConfig) stageElementConfig.getStageType();
    if (integrationStage.getInfrastructure() == null) {
      throw new CIStageExecutionException("Input infrastructure is not set");
    } else {
      if (integrationStage.getInfrastructure().getType() == KUBERNETES_DIRECT) {
        K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) integrationStage.getInfrastructure();

        if (!k8sDirectInfraYaml.getSpec().getConnectorRef().isExpression()) {
          final String clusterConnectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
          String fullQualifiedDomainName =
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
              + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + clusterConnectorRef;
          result.add(convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
              fullQualifiedDomainName, ParameterField.createValueField(clusterConnectorRef),
              EntityTypeProtoEnum.CONNECTORS));
        }
      }
      Optional<EntityDetailProtoDTO> harnessImageConnectorOptional =
          getHarnessImageConnectorReferredEntity(filterCreationContext, integrationStage.getInfrastructure(),
              accountIdentifier, orgIdentifier, projectIdentifier);
      harnessImageConnectorOptional.ifPresent(result::add);
    }

    return result;
  }

  private Optional<EntityDetailProtoDTO> getHarnessImageConnectorReferredEntity(
      FilterCreationContext filterCreationContext, Infrastructure infrastructure, String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    Optional<EntityDetailProtoDTO> optionalHarnessImageConnector = Optional.empty();
    Optional<ParameterField<String>> optionalHarnessImageConnectorValue =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);

    if (optionalHarnessImageConnectorValue.isPresent()) {
      String fullQualifiedDomainName =
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
          + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE
          + PATH_CONNECTOR + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR;
      if (infrastructure.getType() == VM) {
        fullQualifiedDomainName += YAMLFieldNameConstants.SPEC + PATH_CONNECTOR;
      }
      fullQualifiedDomainName += YAMLFieldNameConstants.HARNESS_IMAGE_CONNECTOR_REF;
      optionalHarnessImageConnector =
          Optional.of(convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
              fullQualifiedDomainName, optionalHarnessImageConnectorValue.get(), EntityTypeProtoEnum.CONNECTORS));
    }
    return optionalHarnessImageConnector;
  }
}
