/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType.S3URL;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.cloudformation.CloudformationTaskType.CREATE_STACK;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.S3UrlStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationCreateStackPassThroughData;
import io.harness.cdng.provision.cloudformation.beans.CloudFormationInheritOutput;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFileDelegateConfig;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesTaskParams;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.aws.s3.S3FileDetailResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.cloudformation.CloudformationCommandUnit;
import io.harness.delegate.task.cloudformation.CloudformationTaskNGParameters;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.validator.NGRegexValidatorConstants;

import software.wings.beans.TaskType;
import software.wings.sm.states.provision.S3UriParser;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.s3.AmazonS3URI;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(CDP)
@Singleton
public class CloudformationStepHelper {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private K8sStepHelper k8sStepHelper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private S3UriParser s3UriParser;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  private static final String CLOUDFORMATION_INHERIT_OUTPUT_FORMAT = "cloudformationInheritOutput_%s";
  private static final String TEMPLATE_FILE_IDENTIFIER = "templateFile";
  private static final String TAGS_FILE_IDENTIFIER = "tagsFile";
  public static final String DEFAULT_TIMEOUT = "10m";

  public TaskChainResponse startChainLink(CloudformationStepExecutor cloudformationStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters) {
    CloudformationCreateStackStepParameters cloudformationCreateStackStepParameters =
        (CloudformationCreateStackStepParameters) stepElementParameters.getSpec();
    CloudformationCreateStackStepConfigurationParameters stepConfiguration =
        cloudformationCreateStackStepParameters.getConfiguration();
    CloudformationTemplateFileSpec cloudformationTemplateFileSpec = stepConfiguration.getTemplateFile().getSpec();

    ConnectorInfoDTO connectorDTO =
        cdStepHelper.getConnector(getParameterFieldValue(stepConfiguration.getConnectorRef()), ambiance);

    if (!(connectorDTO.getConnectorConfig() instanceof AwsConnectorDTO)) {
      throw new InvalidRequestException("Invalid connector selected in CloudFormation step. Select AWS connector");
    }

    List<GitFetchFilesConfig> gitFetchFilesConfigs = getParametersGitFetchFileConfigs(ambiance, stepConfiguration);
    if (isTemplateStoredOnGit(cloudformationTemplateFileSpec)) {
      gitFetchFilesConfigs.add(getTemplateGitFetchFileConfig(
          ambiance, (RemoteCloudformationTemplateFileSpec) cloudformationTemplateFileSpec));
    }
    if (areTagsStoredOnGit(stepConfiguration)) {
      gitFetchFilesConfigs.add(getTagsGitFetchFileConfig(
          ambiance, (RemoteCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec()));
    }
    CloudFormationCreateStackPassThroughData passThroughData =
        getCloudFormationCreateStackPassThroughData(stepConfiguration);

    if (isNotEmpty(gitFetchFilesConfigs)) {
      return getGitFetchFileTaskChainResponse(ambiance, gitFetchFilesConfigs, stepElementParameters, passThroughData);
    } else {
      List<AwsS3FetchFileDelegateConfig> awsS3FetchFileDelegateConfigs =
          getAwsS3FetchFileDelegateConfigs(ambiance, stepConfiguration);
      if (isNotEmpty(awsS3FetchFileDelegateConfigs)) {
        return getS3FetchFileTaskChainResponse(
            ambiance, awsS3FetchFileDelegateConfigs, stepElementParameters, passThroughData, null);
      }
    }
    String templateBody = null;
    String templateUrl = null;
    if (cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.Inline)) {
      templateBody = getParameterFieldValue(
          ((InlineCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getTemplateBody());
    } else if (cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.S3Url)) {
      templateUrl = getParameterFieldValue(
          ((S3UrlCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getTemplateUrl());
    }

    String tags = null;
    if ((stepConfiguration.getTags() != null)
        && stepConfiguration.getTags().getType().equals(CloudformationTagsFileTypes.Inline)) {
      tags = getParameterFieldValue(
          ((InlineCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec()).getContent());
    }

    populatePassThroughData(passThroughData, templateBody, templateUrl, tags);
    CloudformationTaskNGParameters cloudformationTaskNGParameters = getCloudformationTaskNGParameters(ambiance,
        stepElementParameters, (AwsConnectorDTO) connectorDTO.getConnectorConfig(),
        getInlineParameters(stepConfiguration), templateBody, templateUrl, tags, null);

    return cloudformationStepExecutor.executeCloudformationTask(
        ambiance, stepElementParameters, cloudformationTaskNGParameters, passThroughData);
  }

  public TaskChainResponse executeNextLink(CloudformationStepExecutor cloudformationStepExecutor, Ambiance ambiance,
      StepElementParameters stepElementParameters, PassThroughData passThroughData,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    CloudformationCreateStackStepParameters cloudformationCreateStackStepParameters =
        (CloudformationCreateStackStepParameters) stepElementParameters.getSpec();
    CloudformationCreateStackStepConfigurationParameters stepConfiguration =
        cloudformationCreateStackStepParameters.getConfiguration();
    CloudFormationCreateStackPassThroughData cloudFormationCreateStackPassThroughData =
        (CloudFormationCreateStackPassThroughData) passThroughData;
    UnitProgressData unitProgressData = null;
    try {
      ResponseData responseData = responseDataSupplier.get();
      if (responseData instanceof GitFetchResponse) {
        unitProgressData = ((GitFetchResponse) responseData).getUnitProgressData();
        return handleGitFetchResponse(cloudformationStepExecutor, ambiance, stepElementParameters, stepConfiguration,
            cloudFormationCreateStackPassThroughData, (GitFetchResponse) responseData);
      } else if (responseData instanceof AwsS3FetchFilesResponse) {
        unitProgressData = ((AwsS3FetchFilesResponse) responseData).getUnitProgressData();
        return handleAwsS3FetchFileResponse(cloudformationStepExecutor, ambiance, stepElementParameters,
            stepConfiguration, cloudFormationCreateStackPassThroughData, (AwsS3FetchFilesResponse) responseData);
      } else {
        String errorMessage = "Unknown Error";
        return TaskChainResponse.builder()
            .chainEnd(true)
            .passThroughData(StepExceptionPassThroughData.builder()
                                 .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
                                 .errorMessage(errorMessage)
                                 .build())
            .build();
      }
    } catch (TaskNGDataException e) {
      log.error(format("Exception in create stack step: %s", e.getMessage()));
      return getExceptionTaskChainResponse(ambiance, e.getCommandUnitsProgress(), e);
    } catch (Exception e) {
      log.error(format("Exception in create stack step: %s", e.getMessage()));
      return getExceptionTaskChainResponse(ambiance, unitProgressData, e);
    }
  }

  private TaskChainResponse getExceptionTaskChainResponse(
      Ambiance ambiance, UnitProgressData unitProgressData, Exception e) {
    return TaskChainResponse.builder()
        .chainEnd(true)
        .passThroughData(
            StepExceptionPassThroughData.builder()
                .unitProgressData(
                    cdStepHelper.completeUnitProgressData(unitProgressData, ambiance, ExceptionUtils.getMessage(e)))
                .errorMessage(e.getCause() != null ? String.format("%s: %s", e.getMessage(), e.getCause().getMessage())
                                                   : e.getMessage())
                .build())
        .build();
  }

  public StepResponse getFailureResponse(List<UnitProgress> unitProgresses, String errorMessage) {
    return StepResponse.builder()
        .unitProgressList(unitProgresses)
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(errorMessage).build())
        .build();
  }

  public void saveCloudFormationInheritOutput(CloudformationCreateStackStepConfigurationParameters configuration,
      String provisionerIdentifier, Ambiance ambiance, boolean stackExisted) {
    CloudFormationInheritOutput cloudFormationInheritOutput =
        CloudFormationInheritOutput.builder()
            .stackName(getParameterFieldValue(configuration.getStackName()))
            .connectorRef(getParameterFieldValue(configuration.getConnectorRef()))
            .region(getParameterFieldValue(configuration.getRegion()))
            .roleArn(getParameterFieldValue(configuration.getRoleArn()))
            .existingStack(stackExisted)
            .build();
    String identifier = generateIdentifier(provisionerIdentifier, ambiance);
    String inheritOutputName = format(CLOUDFORMATION_INHERIT_OUTPUT_FORMAT, identifier);
    executionSweepingOutputService.consume(
        ambiance, inheritOutputName, cloudFormationInheritOutput, StepOutcomeGroup.STAGE.name());
  }

  public CloudFormationInheritOutput getSavedCloudFormationInheritOutput(
      String provisionerIdentifier, Ambiance ambiance) {
    String identifier = generateIdentifier(provisionerIdentifier, ambiance);
    String inheritOutputName = format(CLOUDFORMATION_INHERIT_OUTPUT_FORMAT, identifier);
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(inheritOutputName));
    if (!output.isFound()) {
      return null;
    }
    return (CloudFormationInheritOutput) output.getOutput();
  }

  public CloudformationConfig getCloudformationConfig(Ambiance ambiance, StepElementParameters stepParameters,
      CloudFormationCreateStackPassThroughData passThroughData) {
    CloudformationCreateStackStepParameters cloudformationCreateStackStepParameters =
        (CloudformationCreateStackStepParameters) stepParameters.getSpec();
    CloudformationCreateStackStepConfigurationParameters stepConfiguration =
        cloudformationCreateStackStepParameters.getConfiguration();

    return CloudformationConfig.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
        .provisionerIdentifier(
            getParameterFieldValue(cloudformationCreateStackStepParameters.getProvisionerIdentifier()))
        .stageExecutionId(AmbianceUtils.getStageExecutionIdForExecutionMode(ambiance))
        .connectorRef(getParameterFieldValue(stepConfiguration.getConnectorRef()))
        .region(getParameterFieldValue(stepConfiguration.getRegion()))
        .templateUrl(passThroughData.getTemplateUrl())
        .templateBody(passThroughData.getTemplateBody())
        .parametersFiles(passThroughData.getParametersFilesContent())
        .roleArn(getParameterFieldValue(stepConfiguration.getRoleArn()))
        .stackName(getParameterFieldValue(stepConfiguration.getStackName()))
        .parameterOverrides(getInlineParameters(stepConfiguration))
        .capabilities(getParameterFieldValue(stepConfiguration.getCapabilities()))
        .tags(isNotEmpty(passThroughData.getTags()) ? renderValue(ambiance, passThroughData.getTags())
                                                    : passThroughData.getTags())
        .stackStatusesToMarkAsSuccess(getParameterFieldValue(stepConfiguration.getSkipOnStackStatuses()))
        .build();
  }

  private List<AwsS3FetchFileDelegateConfig> getAwsS3FetchFileDelegateConfigs(
      Ambiance ambiance, CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    List<AwsS3FetchFileDelegateConfig> awsS3FetchFileDelegateConfigs = new ArrayList<>();
    List<AwsS3FetchFileDelegateConfig> parametersS3FetchFileDelegateConfigs;
    if (isNotEmpty(stepConfiguration.getParameters())) {
      parametersS3FetchFileDelegateConfigs =
          stepConfiguration.getParameters()
              .values()
              .stream()
              .filter(cloudformationParametersFileSpec
                  -> cloudformationParametersFileSpec.getStore().getSpec().getKind().equals(ManifestStoreType.S3URL))
              .map(cloudformationParametersFileSpec
                  -> getParametersAwsS3FetchFileDelegateConfig(cloudformationParametersFileSpec, ambiance))
              .collect(Collectors.toList());
    } else {
      parametersS3FetchFileDelegateConfigs = new ArrayList<>();
    }
    if (areTagsStoredOnS3(stepConfiguration)) {
      awsS3FetchFileDelegateConfigs.add(getTagsAwsS3FetchFileDelegateConfig(
          (RemoteCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec(), ambiance));
    }
    awsS3FetchFileDelegateConfigs.addAll(parametersS3FetchFileDelegateConfigs);

    return awsS3FetchFileDelegateConfigs;
  }

  private AwsS3FetchFileDelegateConfig getParametersAwsS3FetchFileDelegateConfig(
      CloudformationParametersFileSpec cloudformationParametersFileSpec, Ambiance ambiance) {
    S3UrlStoreConfig s3UrlStoreConfig = (S3UrlStoreConfig) cloudformationParametersFileSpec.getStore().getSpec();
    return getAwsS3FetchFileDelegateConfig(
        cloudformationParametersFileSpec.getIdentifier(), ambiance, s3UrlStoreConfig);
  }

  private AwsS3FetchFileDelegateConfig getTagsAwsS3FetchFileDelegateConfig(
      RemoteCloudformationTagsFileSpec cloudformationTagsFileSpec, Ambiance ambiance) {
    S3UrlStoreConfig s3UrlStoreConfig = (S3UrlStoreConfig) cloudformationTagsFileSpec.getStore().getSpec();
    return getAwsS3FetchFileDelegateConfig(TAGS_FILE_IDENTIFIER, ambiance, s3UrlStoreConfig);
  }

  private AwsS3FetchFileDelegateConfig getAwsS3FetchFileDelegateConfig(
      String identifier, Ambiance ambiance, S3UrlStoreConfig s3UrlStoreConfig) {
    AwsConnectorDTO awsConnectorDTO = getAwsConnectorConfig(ambiance, s3UrlStoreConfig.getConnectorRef());
    return AwsS3FetchFileDelegateConfig.builder()
        .identifier(identifier)
        .region(getParameterFieldValue(s3UrlStoreConfig.getRegion()))
        .awsConnector(awsConnectorDTO)
        .encryptionDetails(getAwsEncryptionDetails(ambiance, awsConnectorDTO))
        .fileDetails(getParameterFieldValue(s3UrlStoreConfig.getUrls())
                         .stream()
                         .map(this::getS3FileDetails)
                         .collect(Collectors.toList()))
        .build();
  }

  private CloudFormationCreateStackPassThroughData getCloudFormationCreateStackPassThroughData(
      CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    boolean hasS3Files = hasS3StoredParameters(stepConfiguration) || areTagsStoredOnS3(stepConfiguration);
    boolean hasGitFiles = hasGitStoredParameters(stepConfiguration) || areTagsStoredOnGit(stepConfiguration)
        || isTemplateStoredOnGit(stepConfiguration.getTemplateFile().getSpec());

    CloudFormationCreateStackPassThroughData passThroughData =
        CloudFormationCreateStackPassThroughData.builder().hasGitFiles(hasGitFiles).hasS3Files(hasS3Files).build();

    if (isNotEmpty(stepConfiguration.getParameters())) {
      stepConfiguration.getParameters().values().forEach(cloudformationParametersFileSpec -> {
        String specKind = cloudformationParametersFileSpec.getStore().getSpec().getKind();
        if (ManifestStoreType.isInGitSubset(specKind) || specKind.equals(ManifestStoreType.S3URL)) {
          passThroughData.getParametersFilesContent().put(
              cloudformationParametersFileSpec.getIdentifier(), new ArrayList<>());
        }
      });
    }
    return passThroughData;
  }

  private boolean hasS3StoredParameters(CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    return stepConfiguration.getParameters() != null
        && stepConfiguration.getParameters().values().stream().anyMatch(cloudformationParametersFileSpec
            -> cloudformationParametersFileSpec.getStore().getSpec().getKind().equals(ManifestStoreType.S3URL));
  }

  private boolean hasGitStoredParameters(CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    return stepConfiguration.getParameters() != null
        && stepConfiguration.getParameters().values().stream().anyMatch(cloudformationParametersFileSpec
            -> ManifestStoreType.isInGitSubset(cloudformationParametersFileSpec.getStore().getSpec().getKind()));
  }

  private GitFetchFilesConfig getTemplateGitFetchFileConfig(
      Ambiance ambiance, RemoteCloudformationTemplateFileSpec cloudformationTemplateFileSpec) {
    return GitFetchFilesConfig.builder()
        .manifestType("Cloudformation Template")
        .identifier(TEMPLATE_FILE_IDENTIFIER)
        .gitStoreDelegateConfig(
            getGitStoreDelegateConfig(cloudformationTemplateFileSpec.getStore().getSpec(), ambiance))
        .build();
  }

  private GitFetchFilesConfig getTagsGitFetchFileConfig(
      Ambiance ambiance, RemoteCloudformationTagsFileSpec cloudformationTagsFileSpec) {
    return GitFetchFilesConfig.builder()
        .manifestType("Cloudformation tags")
        .identifier(TAGS_FILE_IDENTIFIER)
        .gitStoreDelegateConfig(getGitStoreDelegateConfig(cloudformationTagsFileSpec.getStore().getSpec(), ambiance))
        .build();
  }

  @NotNull
  private List<GitFetchFilesConfig> getParametersGitFetchFileConfigs(
      Ambiance ambiance, CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    if (isNotEmpty(stepConfiguration.getParameters())) {
      return stepConfiguration.getParameters()
          .values()
          .stream()
          .filter(cloudformationParametersFileSpec
              -> ManifestStoreType.isInGitSubset(cloudformationParametersFileSpec.getStore().getSpec().getKind()))
          .map(cloudformationParametersFileSpec
              -> GitFetchFilesConfig.builder()
                     .manifestType("Cloudformation Parameters")
                     .identifier(cloudformationParametersFileSpec.getIdentifier())
                     .gitStoreDelegateConfig(
                         getGitStoreDelegateConfig(cloudformationParametersFileSpec.getStore().getSpec(), ambiance))
                     .build())
          .collect(Collectors.toList());
    } else {
      return new ArrayList<>();
    }
  }

  private CloudformationTaskNGParameters getCloudformationTaskNGParameters(Ambiance ambiance,
      StepElementParameters stepElementParameters, AwsConnectorDTO awsConnectorDTO, Map<String, String> parameters,
      String templateBody, String templateUrl, String tags, CommandUnitsProgress commandUnitsProgress) {
    CloudformationCreateStackStepParameters cloudformationCreateStackStepParameters =
        (CloudformationCreateStackStepParameters) stepElementParameters.getSpec();
    CloudformationCreateStackStepConfigurationParameters stepConfiguration =
        cloudformationCreateStackStepParameters.getConfiguration();
    List<StackStatus> stackStatuses = new ArrayList<>();
    if (isNotEmpty(getParameterFieldValue(stepConfiguration.getSkipOnStackStatuses()))) {
      stackStatuses = getParameterFieldValue(stepConfiguration.getSkipOnStackStatuses())
                          .stream()
                          .map(StackStatus::fromValue)
                          .collect(Collectors.toList());
    }
    CloudformationTaskNGParameters cloudformationTaskNGParameters =
        CloudformationTaskNGParameters.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .taskType(CREATE_STACK)
            .cfCommandUnit(CloudformationCommandUnit.CreateStack)
            .templateUrl(templateUrl)
            .templateBody(templateBody)
            .awsConnector(awsConnectorDTO)
            .encryptedDataDetails(getAwsEncryptionDetails(ambiance, awsConnectorDTO))
            .region(getParameterFieldValue(stepConfiguration.getRegion()))
            .cloudFormationRoleArn(getParameterFieldValue(stepConfiguration.getRoleArn()))
            .stackName(getParameterFieldValue(stepConfiguration.getStackName()))
            .parameters(parameters)
            .capabilities(getParameterFieldValue(stepConfiguration.getCapabilities()))
            .tags(tags)
            .stackStatusesToMarkAsSuccess(stackStatuses)
            .timeoutInMs(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), DEFAULT_TIMEOUT))
            .commandUnitsProgress(commandUnitsProgress)
            .build();
    cdExpressionResolver.updateExpressions(ambiance, cloudformationTaskNGParameters);
    return cloudformationTaskNGParameters;
  }

  private TaskChainResponse handleAwsS3FetchFileResponse(CloudformationStepExecutor cloudformationStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters,
      CloudformationCreateStackStepConfigurationParameters stepConfiguration,
      CloudFormationCreateStackPassThroughData cloudFormationCreateStackPassThroughData,
      AwsS3FetchFilesResponse responseData) {
    Set<String> identifiers = cloudFormationCreateStackPassThroughData.getParametersFilesContent().keySet();
    responseData.getS3filesDetails().forEach((identifier, s3FileDetailResponses) -> {
      if (identifiers.contains(identifier)) {
        cloudFormationCreateStackPassThroughData.getParametersFilesContent().put(identifier,
            s3FileDetailResponses.stream().map(S3FileDetailResponse::getFileContent).collect(Collectors.toList()));
      }
    });

    Map<String, String> parameters =
        getParameters(ambiance, stepConfiguration, cloudFormationCreateStackPassThroughData);

    String templateBody = null;
    String templateUrl = null;
    CloudformationTemplateFileSpec cloudformationTemplateFileSpec = stepConfiguration.getTemplateFile().getSpec();
    if (isNotEmpty(cloudFormationCreateStackPassThroughData.getTemplateBody())) {
      templateBody = cloudFormationCreateStackPassThroughData.getTemplateBody();
    } else if (cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.Inline)) {
      templateBody = getParameterFieldValue(
          ((InlineCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getTemplateBody());
    } else if (cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.S3Url)) {
      templateUrl = getParameterFieldValue(
          ((S3UrlCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getTemplateUrl());
    }

    String tags = null;
    if (cloudFormationCreateStackPassThroughData.getTags() != null) {
      tags = cloudFormationCreateStackPassThroughData.getTags();
    } else if (isNotEmpty(responseData.getS3filesDetails().get(TAGS_FILE_IDENTIFIER))) {
      tags = responseData.getS3filesDetails().get(TAGS_FILE_IDENTIFIER).get(0).getFileContent();
    } else if (stepConfiguration.getTags() != null
        && stepConfiguration.getTags().getType().equals(CloudformationTagsFileTypes.Inline)) {
      tags = getParameterFieldValue(
          ((InlineCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec()).getContent());
    }

    populatePassThroughData(cloudFormationCreateStackPassThroughData, templateBody, templateUrl, tags);
    AwsConnectorDTO awsConnectorDTO = getAwsConnectorConfig(ambiance, stepConfiguration.getConnectorRef());
    CloudformationTaskNGParameters cloudformationTaskNGParameters =
        getCloudformationTaskNGParameters(ambiance, stepElementParameters, awsConnectorDTO, parameters, templateBody,
            templateUrl, tags, UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));

    return cloudformationStepExecutor.executeCloudformationTask(
        ambiance, stepElementParameters, cloudformationTaskNGParameters, cloudFormationCreateStackPassThroughData);
  }

  private TaskChainResponse handleGitFetchResponse(CloudformationStepExecutor cloudformationStepExecutor,
      Ambiance ambiance, StepElementParameters stepElementParameters,
      CloudformationCreateStackStepConfigurationParameters stepConfiguration,
      CloudFormationCreateStackPassThroughData cloudFormationCreateStackPassThroughData,
      GitFetchResponse responseData) {
    Map<String, FetchFilesResult> filesFromMultipleRepo = responseData.getFilesFromMultipleRepo();

    Set<String> identifiers = cloudFormationCreateStackPassThroughData.getParametersFilesContent().keySet();
    filesFromMultipleRepo.forEach((s, fetchFilesResult) -> {
      if (identifiers.contains(s)) {
        cloudFormationCreateStackPassThroughData.getParametersFilesContent().put(
            s, fetchFilesResult.getFiles().stream().map(GitFile::getFileContent).collect(Collectors.toList()));
      }
    });

    if (cloudFormationCreateStackPassThroughData.hasS3Files()) {
      if (filesFromMultipleRepo.get(TEMPLATE_FILE_IDENTIFIER) != null) {
        cloudFormationCreateStackPassThroughData.setTemplateBody(
            filesFromMultipleRepo.get(TEMPLATE_FILE_IDENTIFIER).getFiles().get(0).getFileContent());
      }
      if (filesFromMultipleRepo.get(TAGS_FILE_IDENTIFIER) != null) {
        cloudFormationCreateStackPassThroughData.setTags(
            filesFromMultipleRepo.get(TAGS_FILE_IDENTIFIER).getFiles().get(0).getFileContent());
      }
      return getS3FetchFileTaskChainResponse(ambiance, getAwsS3FetchFileDelegateConfigs(ambiance, stepConfiguration),
          stepElementParameters, cloudFormationCreateStackPassThroughData,
          UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));
    }

    Map<String, String> parameters =
        getParameters(ambiance, stepConfiguration, cloudFormationCreateStackPassThroughData);

    String templateBody = null;
    String templateUrl = null;

    CloudformationTemplateFileSpec cloudformationTemplateFileSpec = stepConfiguration.getTemplateFile().getSpec();
    if (filesFromMultipleRepo.get(TEMPLATE_FILE_IDENTIFIER) != null) {
      templateBody = filesFromMultipleRepo.get(TEMPLATE_FILE_IDENTIFIER).getFiles().get(0).getFileContent();
    } else if (cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.Inline)) {
      templateBody = getParameterFieldValue(
          ((InlineCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getTemplateBody());
    } else if (cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.S3Url)) {
      templateUrl = getParameterFieldValue(
          ((S3UrlCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getTemplateUrl());
    }

    String tags = null;
    if (cloudFormationCreateStackPassThroughData.getTags() != null) {
      tags = cloudFormationCreateStackPassThroughData.getTags();
    } else if (filesFromMultipleRepo.get(TAGS_FILE_IDENTIFIER) != null) {
      tags = filesFromMultipleRepo.get(TAGS_FILE_IDENTIFIER).getFiles().get(0).getFileContent();
    } else if (stepConfiguration.getTags() != null
        && stepConfiguration.getTags().getType().equals(CloudformationTagsFileTypes.Inline)) {
      tags = getParameterFieldValue(
          ((InlineCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec()).getContent());
    }

    populatePassThroughData(cloudFormationCreateStackPassThroughData, templateBody, templateUrl, tags);
    AwsConnectorDTO awsConnectorDTO = getAwsConnectorConfig(ambiance, stepConfiguration.getConnectorRef());
    CloudformationTaskNGParameters cloudformationTaskNGParameters =
        getCloudformationTaskNGParameters(ambiance, stepElementParameters, awsConnectorDTO, parameters, templateBody,
            templateUrl, tags, UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));

    return cloudformationStepExecutor.executeCloudformationTask(
        ambiance, stepElementParameters, cloudformationTaskNGParameters, cloudFormationCreateStackPassThroughData);
  }

  private void populatePassThroughData(
      CloudFormationCreateStackPassThroughData cloudFormationCreateStackPassThroughData, String templateBody,
      String templateUrl, String tags) {
    cloudFormationCreateStackPassThroughData.setTemplateBody(templateBody);
    cloudFormationCreateStackPassThroughData.setTemplateUrl(templateUrl);
    cloudFormationCreateStackPassThroughData.setTags(tags);
  }

  @NotNull
  private Map<String, String> getParameters(Ambiance ambiance,
      CloudformationCreateStackStepConfigurationParameters stepConfiguration,
      CloudFormationCreateStackPassThroughData cloudFormationCreateStackPassThroughData) {
    Map<String, String> parameters = new HashMap<>();
    cloudFormationCreateStackPassThroughData.getParametersFilesContent().forEach(
        (s, parametersFiles)
            -> parametersFiles.forEach(
                parametersFile -> parameters.putAll(getParametersFromJson(ambiance, parametersFile))));
    parameters.putAll(getInlineParameters(stepConfiguration));
    return parameters;
  }

  private AwsConnectorDTO getAwsConnectorConfig(Ambiance ambiance, ParameterField<String> awsConnectorRef) {
    return (AwsConnectorDTO) cdStepHelper.getConnector(getParameterFieldValue(awsConnectorRef), ambiance)
        .getConnectorConfig();
  }

  private Map<String, String> getInlineParameters(
      CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    Map<String, Object> parameters = stepConfiguration.getParameterOverrides();
    Map<String, String> inlineParameters = new HashMap<>();
    if (isNotEmpty(parameters)) {
      parameters.keySet().forEach(
          key -> inlineParameters.put(key, ((ParameterField<?>) parameters.get(key)).getValue().toString()));
    }
    return inlineParameters;
  }

  public Map<String, String> getParametersFromJson(Ambiance ambiance, String parametersJson) {
    Map<String, String> parametersMap = new HashMap<>();
    ObjectMapper mapper = new ObjectMapper();
    List<Parameter> parameters;
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    try {
      parameters = mapper.readValue(renderValue(ambiance, parametersJson), new TypeReference<List<Parameter>>() {});
    } catch (IOException e) {
      throw new InvalidArgumentsException("Failed to Deserialize json \n" + e.getMessage());
    }
    parameters.forEach(parameter -> parametersMap.put(parameter.getParameterKey(), parameter.getParameterValue()));
    return parametersMap;
  }

  private GitStoreDelegateConfig getGitStoreDelegateConfig(StoreConfig store, Ambiance ambiance) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    cdStepHelper.validateGitStoreConfig(gitStoreConfig);
    String connectorId = getParameterFieldValue(gitStoreConfig.getConnectorRef());
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    String repoName = gitStoreConfig.getRepoName() != null ? gitStoreConfig.getRepoName().getValue() : null;
    if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
      String repoUrl = cdStepHelper.getGitRepoUrl(gitConfigDTO, repoName);
      gitConfigDTO.setUrl(repoUrl);
      gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
    }
    ScmConnector scmConnector = cdStepHelper.getScmConnector(
        (ScmConnector) connectorDTO.getConnectorConfig(), basicNGAccessObject.getAccountIdentifier(), gitConfigDTO);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(scmConnector, sshKeySpecDTO, basicNGAccessObject);
    return GitStoreDelegateConfig.builder()
        .gitConfigDTO(scmConnector)
        .sshKeySpecDTO(sshKeySpecDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .fetchType(gitStoreConfig.getGitFetchType())
        .branch(getParameterFieldValue(gitStoreConfig.getBranch()))
        .commitId(getParameterFieldValue(gitStoreConfig.getCommitId()))
        .paths(getParameterFieldValue(gitStoreConfig.getPaths()))
        .connectorName(connectorDTO.getName())
        .build();
  }

  private TaskChainResponse getGitFetchFileTaskChainResponse(Ambiance ambiance,
      List<GitFetchFilesConfig> gitFetchFilesConfigs, StepElementParameters stepElementParameters,
      CloudFormationCreateStackPassThroughData passThroughData) {
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .closeLogStream(!passThroughData.hasS3Files())
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
            Arrays.asList(K8sCommandUnitConstants.FetchFiles, CloudformationCommandUnit.CreateStack.name()),
            TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName(),
            StepUtils.getTaskSelectors(stepElementParameters.getDelegateSelectors()),
            stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(passThroughData)
        .build();
  }

  private TaskChainResponse getS3FetchFileTaskChainResponse(Ambiance ambiance,
      List<AwsS3FetchFileDelegateConfig> awsS3FetchFileDelegateConfigs, StepElementParameters stepElementParameters,
      CloudFormationCreateStackPassThroughData passThroughData, CommandUnitsProgress commandUnitsProgress) {
    AwsS3FetchFilesTaskParams awsS3FetchFilesTaskParams = AwsS3FetchFilesTaskParams.builder()
                                                              .fetchFileDelegateConfigs(awsS3FetchFileDelegateConfigs)
                                                              .shouldOpenLogStream(!passThroughData.hasGitFiles())
                                                              .closeLogStream(true)
                                                              .commandUnitsProgress(commandUnitsProgress)
                                                              .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.FETCH_S3_FILE_TASK_NG.name())
                                  .parameters(new Object[] {awsS3FetchFilesTaskParams})
                                  .build();

    final TaskRequest taskRequest =
        TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData, referenceFalseKryoSerializer,
            Arrays.asList(K8sCommandUnitConstants.FetchFiles, CloudformationCommandUnit.CreateStack.name()),
            TaskType.FETCH_S3_FILE_TASK_NG.getDisplayName(),
            StepUtils.getTaskSelectors(stepElementParameters.getDelegateSelectors()),
            stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(passThroughData)
        .build();
  }

  private boolean isTemplateStoredOnGit(CloudformationTemplateFileSpec cloudformationTemplateFileSpec) {
    return cloudformationTemplateFileSpec.getType().equals(CloudformationTemplateFileTypes.Remote)
        && ManifestStoreType.isInGitSubset(
            ((RemoteCloudformationTemplateFileSpec) cloudformationTemplateFileSpec).getStore().getSpec().getKind());
  }

  private boolean areTagsStoredOnGit(CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    return stepConfiguration.getTags() != null
        && stepConfiguration.getTags().getType().equals(CloudformationTagsFileTypes.Remote)
        && ManifestStoreType.isInGitSubset(
            ((RemoteCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec()).getStore().getSpec().getKind());
  }

  private boolean areTagsStoredOnS3(CloudformationCreateStackStepConfigurationParameters stepConfiguration) {
    return stepConfiguration.getTags() != null
        && stepConfiguration.getTags().getType().equals(CloudformationTagsFileTypes.Remote)
        && ((RemoteCloudformationTagsFileSpec) stepConfiguration.getTags().getSpec())
               .getStore()
               .getType()
               .equals(S3URL);
  }

  private S3FileDetailRequest getS3FileDetails(String s3url) {
    AmazonS3URI amazonS3URI = s3UriParser.parseUrl(s3url);
    return S3FileDetailRequest.builder().bucketName(amazonS3URI.getBucket()).fileKey(amazonS3URI.getKey()).build();
  }

  public List<EncryptedDataDetail> getAwsEncryptionDetails(Ambiance ambiance, AwsConnectorDTO awsConnectorDTO) {
    if (isNotEmpty(awsConnectorDTO.getDecryptableEntities())) {
      return secretManagerClientService.getEncryptionDetails(
          AmbianceUtils.getNgAccess(ambiance), awsConnectorDTO.getDecryptableEntities().get(0));
    } else {
      return emptyList();
    }
  }

  public String generateIdentifier(String provisionerIdentifier, Ambiance ambiance) {
    if (Pattern.matches(NGRegexValidatorConstants.IDENTIFIER_PATTERN, provisionerIdentifier)) {
      return format("%s/%s/%s/%s", AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), provisionerIdentifier);
    } else {
      throw new InvalidRequestException(
          format("Provisioner Identifier cannot contain special characters or spaces: [%s]", provisionerIdentifier));
    }
  }

  public ConnectorInfoDTO getConnectorDTO(String connectorIdentifierRef, Ambiance ambiance) {
    try {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
          connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(
            String.format("Connector not found for identifier : [%s]", connectorIdentifierRef), WingsException.USER);
      }
      ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
      return connectorDTO.get().getConnector();
    } catch (Exception e) {
      log.error(format("Unable to get connector information : [%s] ", connectorIdentifierRef), e);
      throw new InvalidRequestException(format("Unable to get connector information : [%s] ", connectorIdentifierRef));
    }
  }

  public String renderValue(Ambiance ambiance, @NonNull String valueFileContent) {
    return cdExpressionResolver.renderExpression(ambiance, valueFileContent);
  }
}
