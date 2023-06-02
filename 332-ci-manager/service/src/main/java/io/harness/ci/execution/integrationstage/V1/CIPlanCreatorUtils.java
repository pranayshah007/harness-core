/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage.V1;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.yaml.extended.ci.codebase.Build.builder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.beans.Shell;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.VMRuntimeV1;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.states.codebase.ScmGitRefManager;
import io.harness.ci.utils.WebhookTriggerProcessorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.utils.IdentifierGeneratorUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.clone.Clone;
import io.harness.yaml.clone.Ref;
import io.harness.yaml.clone.RefType;
import io.harness.yaml.clone.Strategy;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.Build.BuildBuilder;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.CodeBase.CodeBaseBuilder;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.options.Options;
import io.harness.yaml.repository.Repository;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class CIPlanCreatorUtils {
  @Inject private KryoSerializer kryoSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ScmGitRefManager scmGitRefManager;

  public Optional<CodeBase> getCodebase(PlanCreationContext ctx, Clone stageClone) {
    Dependency globalDependency = ctx.getMetadata().getGlobalDependency();
    Optional<Options> optionalOptions = getDeserializedOptions(globalDependency);
    Options options = optionalOptions.orElse(Options.builder().build());
    Optional<Clone> optionalClone = Optional.ofNullable(options.getClone());
    Clone clone = optionalClone.orElse(Clone.builder().build());
    if (clone.isDisabled() || stageClone.isDisabled()) {
      return Optional.empty();
    }
    Optional<Repository> optionalRepository = Optional.ofNullable(options.getRepository());
    Repository repository = optionalRepository.orElse(Repository.builder().build());
    PipelineStoreType pipelineStoreType = ctx.getPipelineStoreType();
    if (pipelineStoreType == PipelineStoreType.INLINE && optionalRepository.isEmpty()) {
      throw new InvalidRequestException("Repository cannot be empty for inline pipeline if clone is enabled");
    }
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(ctx.getAccountIdentifier())
                                .orgIdentifier(ctx.getOrgIdentifier())
                                .projectIdentifier(ctx.getProjectIdentifier())
                                .build();
    CodeBaseBuilder codeBaseBuilder =
        CodeBase.builder()
            .uuid(stageClone.getUuid())
            .depth(ParameterField.isBlank(stageClone.getDepth()) ? clone.getDepth() : stageClone.getDepth())
            .sslVerify(
                ParameterField.isBlank(stageClone.getInsecure()) ? clone.getInsecure() : stageClone.getInsecure())
            .prCloneStrategy(stageClone.getStrategy() != Strategy.MERGE
                    ? ParameterField.createValueField(stageClone.getStrategy().toPRCloneStrategy())
                    : ParameterField.createValueField(clone.getStrategy().toPRCloneStrategy()));
    switch (pipelineStoreType) {
      case REMOTE:
        codeBaseBuilder = buildCodebaseForRemotePipeline(ctx, ngAccess, repository, clone.getRef(), codeBaseBuilder);
        break;
      case INLINE:
        codeBaseBuilder = buildCodebaseForInlinePipeline(ctx, ngAccess, repository, clone.getRef(), codeBaseBuilder);
        break;
      default:
        throw new InvalidRequestException("Invalid Pipeline Store Type : " + pipelineStoreType);
    }
    return Optional.of(codeBaseBuilder.build());
  }

  public Infrastructure getInfrastructure(RuntimeV1 runtime, PlatformV1 platformV1) {
    Platform platform = platformV1.toPlatform();
    switch (runtime.getType()) {
      case CLOUD:
        return HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(platform))
                      .build())
            .build();
      case MACHINE:
        return DockerInfraYaml.builder()
            .spec(DockerInfraYaml.DockerInfraSpec.builder().platform(ParameterField.createValueField(platform)).build())
            .build();
      case VM:
        VMRuntimeV1 vmRuntime = (VMRuntimeV1) runtime;
        return VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder().poolName(vmRuntime.getSpec().getPool()).build())
                      .build())
            .build();
      default:
        throw new InvalidRequestException("Invalid Runtime - " + runtime.getType());
    }
  }

  public Optional<Options> getDeserializedOptions(Dependency dependency) {
    Optional<Object> optionalOptions = getDeserializedObjectFromDependency(dependency, YAMLFieldNameConstants.OPTIONS);
    Options options = (Options) optionalOptions.orElse(Options.builder().build());
    return Optional.of(options);
  }

  public Optional<Object> getDeserializedObjectFromDependency(Dependency dependency, String key) {
    if (dependency == null || EmptyPredicate.isEmpty(dependency.getMetadataMap())
        || !dependency.getMetadataMap().containsKey(key)) {
      return Optional.empty();
    }
    byte[] bytes = dependency.getMetadataMap().get(key).toByteArray();
    return EmptyPredicate.isEmpty(bytes) ? Optional.empty() : Optional.of(kryoSerializer.asObject(bytes));
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
        JsonNode node = step.getNode().getCurrJsonNode();
        if (node != null && node.isObject() && node.get(YAMLFieldNameConstants.NAME) != null) {
          ObjectNode objectNode = (ObjectNode) node;
          objectNode.put(YAMLFieldNameConstants.IDENTIFIER,
              IdentifierGeneratorUtils.getId(objectNode.get(YAMLFieldNameConstants.NAME).asText()));
        }
        return ExecutionWrapperConfig.builder().uuid(step.getUuid()).step(step.getNode().getCurrJsonNode()).build();
    }
  }

  public static ParameterField<CIShellType> getShell(ParameterField<Shell> shellParameterField) {
    if (ParameterField.isBlank(shellParameterField)) {
      return ParameterField.ofNull();
    }
    return shellParameterField.isExpression()
        ? ParameterField.createExpressionField(
            true, shellParameterField.getExpressionValue(), shellParameterField.getInputSetValidator(), true)
        : ParameterField.createValueField(shellParameterField.getValue().toShellType());
  }

  public static ParameterField<ImagePullPolicy> getImagePullPolicy(ParameterField<PullPolicy> pullParameterField) {
    if (ParameterField.isBlank(pullParameterField)) {
      return ParameterField.ofNull();
    }
    return pullParameterField.isExpression()
        ? ParameterField.createExpressionField(
            true, pullParameterField.getExpressionValue(), pullParameterField.getInputSetValidator(), true)
        : ParameterField.createValueField(pullParameterField.getValue().toImagePullPolicy());
  }

  public boolean shouldCloneManually(PlanCreationContext ctx, CodeBase codeBase) {
    if (codeBase == null) {
      return false;
    }

    switch (ctx.getTriggerInfo().getTriggerType()) {
      case WEBHOOK:
        Dependency globalDependency = ctx.getMetadata().getGlobalDependency();
        Optional<Options> optionalOptions = getDeserializedOptions(globalDependency);
        Options options = optionalOptions.orElse(Options.builder().build());
        Clone clone = options.getClone();
        if (clone == null || ParameterField.isNull(clone.getRef())) {
          return false;
        }
        break;
      default:
    }
    return true;
  }

  private CodeBaseBuilder buildCodebaseForRemotePipeline(PlanCreationContext ctx, BaseNGAccess ngAccess,
      Repository repository, ParameterField<Ref> refField, CodeBaseBuilder builder) {
    GitSyncBranchContext gitSyncBranchContext = deserializeGitSyncBranchContext(ctx.getGitSyncBranchContext());
    if (gitSyncBranchContext == null) {
      throw new InvalidRequestException("Git sync data cannot be null for remote pipeline");
    }
    boolean connectorOverride = !ParameterField.isBlank(repository.getConnector())
        && !repository.getConnector().fetchFinalValue().equals(
            ctx.getMetadata().getMetadata().getPipelineConnectorRef());
    ParameterField<String> repoName = !connectorOverride && ParameterField.isBlank(repository.getName())
        ? ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getRepoName())
        : repository.getName();
    ParameterField<String> connector = ParameterField.isBlank(repository.getConnector())
        ? ParameterField.createValueField(ctx.getMetadata().getMetadata().getPipelineConnectorRef())
        : repository.getConnector();
    return builder
        .build(ParameterField.createValueField(
            getBuildForRemotePipeline(ctx, ngAccess, repository, refField, gitSyncBranchContext, connectorOverride)))
        .repoName(repoName)
        .connectorRef(connector);
  }

  private CodeBaseBuilder buildCodebaseForInlinePipeline(PlanCreationContext ctx, BaseNGAccess ngAccess,
      Repository repository, ParameterField<Ref> refField, CodeBaseBuilder builder) {
    if (ParameterField.isBlank(repository.getConnector())) {
      throw new InvalidRequestException("Connector should not be empty for inline pipeline");
    }
    return builder.build(ParameterField.createValueField(getBuild(ctx, ngAccess, repository, refField)))
        .connectorRef(repository.getConnector())
        .repoName(repository.getName());
  }

  private Build getBuildForRemotePipeline(PlanCreationContext ctx, BaseNGAccess ngAccess, Repository repository,
      ParameterField<Ref> refField, GitSyncBranchContext gitSyncBranchContext, boolean connectorOverride) {
    BuildBuilder builder = builder();
    if (ctx.getTriggerInfo().getTriggerType() != TriggerType.WEBHOOK) {
      if (!connectorOverride && ParameterField.isNull(refField)) {
        return builder.type(BuildType.BRANCH)
            .spec(BranchBuildSpec.builder()
                      .branch(ParameterField.createValueField(gitSyncBranchContext.getGitBranchInfo().getBranch()))
                      .build())
            .build();
      }
    }
    return getBuild(ctx, ngAccess, repository, refField);
  }

  private Build getBuild(
      PlanCreationContext ctx, BaseNGAccess ngAccess, Repository repository, ParameterField<Ref> refField) {
    BuildBuilder builder = builder();

    switch (ctx.getTriggerInfo().getTriggerType()) {
      case WEBHOOK:
        if (ParameterField.isNull(refField)) {
          ParsedPayload parsedPayload = ctx.getTriggerPayload().getParsedPayload();
          WebhookExecutionSource webhookExecutionSource =
              WebhookTriggerProcessorUtils.convertWebhookResponse(parsedPayload);
          switch (webhookExecutionSource.getWebhookEvent().getType()) {
            case PR:
              PRWebhookEvent prWebhookEvent = (PRWebhookEvent) webhookExecutionSource.getWebhookEvent();
              return builder.type(BuildType.PR)
                  .spec(PRBuildSpec.builder()
                            .number(ParameterField.createValueField(String.valueOf(prWebhookEvent.getPullRequestId())))
                            .build())
                  .build();
            case BRANCH:
              BranchWebhookEvent branchWebhookEvent = (BranchWebhookEvent) webhookExecutionSource.getWebhookEvent();
              return builder.type(BuildType.BRANCH)
                  .spec(BranchBuildSpec.builder()
                            .branch(ParameterField.createValueField(branchWebhookEvent.getBranchName()))
                            .build())
                  .build();
            default:
          }
        }
        break;
      default:
        // if reference is null, try to fetch default branch and clone with that
        if (ParameterField.isNull(refField)
            || (refField.getValue().getType() == RefType.BRANCH && isEmpty(refField.getValue().getName()))) {
          Optional<String> optionalDefaultBranch = getDefaultBranchIfApplicable(ngAccess, repository);
          if (optionalDefaultBranch.isPresent()) {
            return builder.type(BuildType.BRANCH)
                .spec(BranchBuildSpec.builder()
                          .branch(ParameterField.createValueField(optionalDefaultBranch.get()))
                          .build())
                .build();
          }
        }
    }

    Ref ref = refField.getValue();
    if (isEmpty(ref.getName()) || ref.getType() == null) {
      throw new InvalidRequestException("Reference value and type cannot be empty");
    }

    ParameterField<String> name = ParameterField.createValueField(ref.getName());
    switch (ref.getType()) {
      case BRANCH:
        builder = builder.type(BuildType.BRANCH).spec(BranchBuildSpec.builder().branch(name).build());
        break;
      case TAG:
        builder = builder.type(BuildType.TAG).spec(TagBuildSpec.builder().tag(name).build());
        break;
      case PR:
        builder = builder.type(BuildType.PR).spec(PRBuildSpec.builder().number(name).build());
        break;
      default:
        throw new InvalidRequestException(String.format("Invalid reference type given: %s", ref.getType()));
    }
    return builder.build();
  }

  private Optional<String> getDefaultBranchIfApplicable(BaseNGAccess ngAccess, Repository repository) {
    if (ParameterField.isNull(repository.getConnector())) {
      return Optional.empty();
    }
    String connectorIdentifier = (String) repository.getConnector().fetchFinalValue();
    String repoName = (String) repository.getName().fetchFinalValue();
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier, true);
    try {
      String defaultBranch = scmGitRefManager.getDefaultBranch(
          scmGitRefManager.getScmConnector(connectorDetails, ngAccess.getAccountIdentifier(), repoName),
          connectorIdentifier);
      return Optional.of(defaultBranch);
    } catch (Exception ex) {
      throw new InvalidRequestException(
          String.format("Cannot find default branch for connector: %s", connectorIdentifier));
    }
  }

  private GitSyncBranchContext deserializeGitSyncBranchContext(ByteString byteString) {
    if (isEmpty(byteString)) {
      return null;
    }
    byte[] bytes = byteString.toByteArray();
    return isEmpty(bytes) ? null : (GitSyncBranchContext) kryoSerializer.asInflatedObject(bytes);
  }

  private String retrieveLastCommitSha(WebhookExecutionSource webhookExecutionSource) {
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
