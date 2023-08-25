/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.k8s.K8sApplyStepInfo;
import io.harness.cdng.k8s.K8sApplyStepNode;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStore.GitStoreBuilder;
import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.data.structure.CompareUtils;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sApplyState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class K8sApplyStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.K8S_APPLY;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    K8sApplyState state = new K8sApplyState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public List<CgEntityId> getReferencedEntities(
      String accountId, Workflow workflow, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    K8sApplyState state = (K8sApplyState) getState(graphNode);
    List<CgEntityId> refs = new ArrayList<>();
    GitFileConfig gitFileConfig = state.getRemoteStepOverride();

    if (gitFileConfig != null) {
      refs.add(CgEntityId.builder().id(gitFileConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build());
    }

    refs.addAll(secretRefUtils.getSecretRefFromExpressions(accountId, getExpressions(graphNode)));
    return refs;
  }

  private ManifestConfigWrapper generateBaseManifestConfigWrapper(StoreConfigWrapper storeConfigWrapper) {
    return ManifestConfigWrapper.builder()
        .manifest(ManifestConfig.builder()
                      .identifier("_values")
                      .type(ManifestConfigType.VALUES)
                      .spec(ValuesManifest.builder().store(ParameterField.createValueField(storeConfigWrapper)).build())
                      .build())
        .build();
  }

  private List<ManifestConfigWrapper> populateOverrideValuesOptions(
      K8sApplyState cgState, Map<CgEntityId, NGYamlFile> migratedEntities, String workflowId) {
    List<ManifestConfigWrapper> manifestConfigWrapperList = new ArrayList<>();
    if (isNotEmpty(cgState.getInlineStepOverride())) {
      manifestConfigWrapperList.add(generateBaseManifestConfigWrapper(
          StoreConfigWrapper.builder()
              .type(StoreConfigType.INLINE)
              .spec(InlineStoreConfig.builder()
                        .content(ParameterField.createValueField(cgState.getInlineStepOverride()))
                        .build())
              .build()));
    } else if (cgState.getRemoteStepOverride() != null) {
      GitFileConfig cgRemoteStepConfig = cgState.getRemoteStepOverride();
      NgEntityDetail connector = getGitConnector(migratedEntities, cgRemoteStepConfig);
      if (connector == null) {
        log.error(
            String.format("We could not migrate the following workflow %s as we could not find the git connector %s",
                workflowId, cgRemoteStepConfig.getConnectorId()));
        return manifestConfigWrapperList;
      }

      GitStoreBuilder builder =
          GitStore.builder()
              .connectorRef(ParameterField.createValueField(connector.getIdentifier()))
              .gitFetchType(cgRemoteStepConfig.isUseBranch() ? FetchType.BRANCH : FetchType.COMMIT)
              .paths(ParameterField.createValueField(List.of(cgRemoteStepConfig.getFilePath())));
      if (isNotEmpty(cgRemoteStepConfig.getRepoName())) {
        builder.repoName(ParameterField.createValueField(cgRemoteStepConfig.getRepoName()));
      }
      if (cgRemoteStepConfig.isUseBranch()) {
        builder.branch(ParameterField.createValueField(cgRemoteStepConfig.getBranch()));
      } else if (isNotEmpty(cgRemoteStepConfig.getCommitId())) {
        builder.commitId(ParameterField.createValueField(cgRemoteStepConfig.getCommitId()));
      }
      manifestConfigWrapperList.add(generateBaseManifestConfigWrapper(
          StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(builder.build()).build()));
    }
    return manifestConfigWrapperList;
  }

  private static NgEntityDetail getGitConnector(
      Map<CgEntityId, NGYamlFile> migratedEntities, GitFileConfig gitFileConfig) {
    CgEntityId connectorId =
        CgEntityId.builder().id(gitFileConfig.getConnectorId()).type(NGMigrationEntityType.CONNECTOR).build();
    if (!migratedEntities.containsKey(connectorId)) {
      return null;
    }
    return migratedEntities.get(connectorId).getNgEntityDetail();
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    K8sApplyState state = (K8sApplyState) getState(graphNode);
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    K8sApplyStepNode k8sApplyStepNode = new K8sApplyStepNode();
    baseSetup(state, k8sApplyStepNode, context.getIdentifierCaseFormat());
    List<ManifestConfigWrapper> manifestConfigWrapperList =
        populateOverrideValuesOptions(state, migratedEntities, context.getWorkflow().getUuid());

    K8sApplyStepInfo k8sApplyStepInfo =
        K8sApplyStepInfo.infoBuilder()
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .filePaths(ParameterField.createValueField(Arrays.stream(state.getFilePaths().split(","))
                                                           .map(String::trim)
                                                           .filter(StringUtils::isNotBlank)
                                                           .collect(Collectors.toList())))
            .skipDryRun(ParameterField.createValueField(state.isSkipDryRun()))
            .skipSteadyStateCheck(ParameterField.createValueField(state.isSkipSteadyStateCheck()))
            .skipRendering(ParameterField.createValueField(state.isSkipRendering()))
            .overrides(manifestConfigWrapperList)
            .build();
    k8sApplyStepNode.setK8sApplyStepInfo(k8sApplyStepInfo);
    return k8sApplyStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    K8sApplyState state1 = (K8sApplyState) getState(stepYaml1);
    K8sApplyState state2 = (K8sApplyState) getState(stepYaml2);
    return StringUtils.compare(state1.getFilePaths(), state2.getFilePaths()) == 0
        && state1.isInheritManifests() == state2.isInheritManifests()
        && StringUtils.compare(state1.getInlineStepOverride(), state2.getInlineStepOverride()) == 0
        && CompareUtils.compareObjects(state1.getRemoteStepOverride(), state2.getRemoteStepOverride());
  }
}
