/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage.V1;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.yaml.extended.ci.codebase.Build.builder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.build.BuildStatusUpdateParameter.BuildStatusUpdateParameterBuilder;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.yaml.extended.clone.Clone;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.repository.Repository;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.cimanager.stages.V1.IntegrationStageNodeV1;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.CodeBase.CodeBaseBuilder;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@UtilityClass
@Slf4j
public class CIPlanCreatorUtils {
  static final String CLONE = "clone";

  public static Optional<CodeBase> getCodebase(PlanCreationContext ctx, Clone clone, KryoSerializer kryoSerializer) {
    if (clone.getDisabled().getValue()) {
      return Optional.empty();
    }
    PipelineStoreType pipelineStoreType = ctx.getPipelineStoreType();
    Optional<Repository> optionalRepository = RunTimeInputHandler.resolveRepository(clone.getRepository());
    CodeBaseBuilder codeBaseBuilder =
        CodeBase.builder()
            .uuid(clone.getUuid())
            .build(ParameterField.createValueField(getBuild(ctx, optionalRepository, kryoSerializer)))
            .depth(ParameterField.createValueField(
                RunTimeInputHandler.resolveIntegerParameter(clone.getDepth(), GIT_CLONE_MANUAL_DEPTH)))
            .sslVerify(ParameterField.createValueField(
                RunTimeInputHandler.resolveBooleanParameter(clone.getInsecure(), false)))
            .prCloneStrategy(ParameterField.createValueField(null));
    switch (pipelineStoreType) {
      case REMOTE:
        codeBaseBuilder = buildRemoteCodebase(ctx, optionalRepository, kryoSerializer, codeBaseBuilder);
        break;
      case INLINE:
        codeBaseBuilder = buildInlineCodebase(optionalRepository, codeBaseBuilder);
        break;
      default:
        throw new InvalidRequestException("Invalid Pipeline Store Type : " + pipelineStoreType);
    }
    return Optional.of(codeBaseBuilder.build());
  }

  public static Infrastructure getInfrastructure(RuntimeV1 runtime, PlatformV1 platformV1) {
    Platform platform = platformV1.toPlatform();
    switch (runtime.getType()) {
      case CLOUD:
        return HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(platform))
                      .build())
            .build();
      case DOCKER:
        return DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder().platform(ParameterField.createValueField(platform)).build())
            .build();
      default:
        throw new InvalidRequestException("Invalid Runtime - " + runtime.getType());
    }
  }

  public static Optional<Object> getDeserializedObjectFromDependency(
      Dependency dependency, KryoSerializer kryoSerializer, String key) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey(key)) {
      return Optional.empty();
    }
    byte[] bytes = dependency.getMetadataMap().get(key).toByteArray();
    return EmptyPredicate.isEmpty(bytes) ? Optional.empty() : Optional.of(kryoSerializer.asObject(bytes));
  }

  public static ExecutionSource buildExecutionSource(
      PlanCreationContext ctx, CodeBase codeBase, ConnectorUtils connectorUtils, String identifier) {
    if (codeBase == null) {
      return null;
    }
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");
    ExecutionTriggerInfo triggerInfo = planCreationContextValue.getMetadata().getTriggerInfo();
    TriggerPayload triggerPayload = planCreationContextValue.getTriggerPayload();
    return IntegrationStageUtils.buildExecutionSource(triggerInfo, triggerPayload, identifier, codeBase.getBuild(),
        codeBase.getConnectorRef().getValue(), connectorUtils, ctx, codeBase);
  }

  public static BuildStatusUpdateParameter getBuildStatusUpdateParameter(
      IntegrationStageNodeV1 stageNode, CodeBase codebase, ExecutionSource executionSource) {
    if (codebase == null) {
      return null;
    }
    BuildStatusUpdateParameterBuilder builder = BuildStatusUpdateParameter.builder()
                                                    .connectorIdentifier(codebase.getConnectorRef().getValue())
                                                    .repoName(codebase.getRepoName().getValue())
                                                    .name(stageNode.getName())
                                                    .identifier(stageNode.getIdentifier());

    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.WEBHOOK) {
      builder = builder.sha(retrieveLastCommitSha((WebhookExecutionSource) executionSource));
    }
    return builder.build();
  }

  public static List<YamlField> getStepYamlFields(YamlField yamlField) {
    List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
  }

  public static ExecutionWrapperConfig getExecutionConfig(YamlField step) {
    if (step.getType() == null) {
      throw new InvalidRequestException("Type cannot be null for CI Step");
    }
    switch (step.getType()) {
      case YAMLFieldNameConstants.PARALLEL:
        List<YamlField> parallelNodes = getStepYamlFields(
            step.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.STEPS));
        ParallelStepElementConfig parallelStepElementConfig =
            ParallelStepElementConfig.builder()
                .sections(
                    parallelNodes.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList()))
                .build();
        return ExecutionWrapperConfig.builder()
            .uuid(step.getUuid())
            .parallel(getJsonNode(parallelStepElementConfig))
            .build();
      case YAMLFieldNameConstants.GROUP:
        List<YamlField> groupNodes = getStepYamlFields(
            step.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.STEPS));
        StepGroupElementConfig stepGroupElementConfig =
            StepGroupElementConfig.builder()
                .identifier(IdentifierGeneratorUtils.getId(step.getNodeName()))
                .name(step.getNodeName())
                .steps(groupNodes.stream().map(CIPlanCreatorUtils::getExecutionConfig).collect(Collectors.toList()))
                .build();
        return ExecutionWrapperConfig.builder()
            .uuid(step.getUuid())
            .stepGroup(getJsonNode(stepGroupElementConfig))
            .build();
      default:
        return ExecutionWrapperConfig.builder().uuid(step.getUuid()).step(step.getNode().getCurrJsonNode()).build();
    }
  }

  private CodeBaseBuilder buildRemoteCodebase(PlanCreationContext ctx, Optional<Repository> optionalRepository,
      KryoSerializer kryoSerializer, CodeBaseBuilder builder) {
    GitSyncBranchContext gitSyncBranchContext =
        deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext(), kryoSerializer);
    if (gitSyncBranchContext == null) {
      throw new InvalidRequestException("Git sync data cannot be null for remote pipeline");
    }
    ParameterField<String> repoName = optionalRepository.isEmpty()
        ? ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getRepoName())
        : optionalRepository.get().getName();
    ParameterField<String> connector = optionalRepository.isEmpty()
        ? ParameterField.createValueField(ctx.getMetadata().getMetadata().getPipelineConnectorRef())
        : optionalRepository.get().getConnector();
    return builder.repoName(repoName).connectorRef(connector);
  }

  private CodeBaseBuilder buildInlineCodebase(Optional<Repository> optionalRepository, CodeBaseBuilder builder) {
    if (optionalRepository.isEmpty()) {
      throw new InvalidRequestException("repository cannot be null for inline pipeline if clone is enabled");
    }
    Repository repository = optionalRepository.get();
    builder = builder
                  .connectorRef(ParameterField.createValueField(RunTimeInputHandler.resolveStringParameter(
                      "connector", CLONE, CLONE, repository.getConnector(), true)))
                  .repoName(ParameterField.createValueField(
                      RunTimeInputHandler.resolveStringParameter("name", CLONE, CLONE, repository.getName(), true)));
    return builder;
  }

  private static Build getBuild(
      PlanCreationContext ctx, Optional<Repository> optionalRepository, KryoSerializer kryoSerializer) {
    GitSyncBranchContext gitSyncBranchContext =
        deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext(), kryoSerializer);
    if (gitSyncBranchContext == null) {
      throw new InvalidRequestException("Git sync data cannot be null for remote pipeline");
    }
    BuildBuilder builder = builder();
    if (optionalRepository.isEmpty()) {
      return builder.type(BuildType.BRANCH)
          .spec(BranchBuildSpec.builder()
                    .branch(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getBranch()))
                    .build())
          .build();
    }
    Repository repository = optionalRepository.get();
    switch (repository.getBuildType()) {
      case BRANCH:
        builder = builder.type(BuildType.BRANCH).spec(BranchBuildSpec.builder().branch(repository.getBranch()).build());
        break;
      case TAG:
        builder = builder.type(BuildType.TAG).spec(TagBuildSpec.builder().tag(repository.getTag()).build());
        break;
      default:
        builder = builder.type(BuildType.PR).spec(PRBuildSpec.builder().number(repository.getPr()).build());
    }
    return builder.build();
  }

  private static GitSyncBranchContext deserializeGitSyncBranchContext(
      ByteString byteString, KryoSerializer kryoSerializer) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }

  private static String retrieveLastCommitSha(WebhookExecutionSource webhookExecutionSource) {
    if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.PR) {
      PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
      return prWebhookEvent.getBaseAttributes().getAfter();
    } else if (webhookExecutionSource.getWebhookEvent().getType() == WebhookEvent.Type.BRANCH) {
      BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
      return branchWebhookEvent.getBaseAttributes().getAfter();
    }
    log.error("Non supported event type, status will be empty");
    return "";
  }

  private static JsonNode getJsonNode(Object object) {
    try {
      String json = JsonPipelineUtils.writeJsonString(object);
      return JsonPipelineUtils.getMapper().readTree(json);
    } catch (IOException e) {
      throw new CIStageExecutionException("Failed to serialise node", e);
    }
  }
}
