/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.visitor.helpers.SecretConnectorRefExtractorHelper;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfField;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.AZURE_REPO)
@OneOfField(fields = {"paths", "folderPath"})
@OneOfField(fields = {"branch", "commitId"})
@SimpleVisitorHelper(helperClass = SecretConnectorRefExtractorHelper.class)
@TypeAlias("azureRepoStore")
@OwnedBy(GITOPS)
@RecasterAlias("io.harness.cdng.manifest.yaml.AzureRepoStore")
public class AzureRepoStore implements GitStoreConfig, Visitable, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Wither
  private ParameterField<String> connectorRef;

  @NotNull @Wither private FetchType gitFetchType;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> branch;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> commitId;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @Wither
  private ParameterField<List<String>> paths;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> folderPath;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither private ParameterField<String> repoName;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public GitStoreConfigDTO toGitStoreConfigDTO() {
    return AzureRepoStoreDTO.builder()
        .branch(ParameterFieldHelper.getParameterFieldValue(branch))
        .commitId(ParameterFieldHelper.getParameterFieldValue(commitId))
        .connectorRef(ParameterFieldHelper.getParameterFieldValue(connectorRef))
        .folderPath(ParameterFieldHelper.getParameterFieldValue(folderPath))
        .gitFetchType(gitFetchType)
        .paths(ParameterFieldHelper.getParameterFieldValue(paths))
        .repoName(ParameterFieldHelper.getParameterFieldValue(repoName))
        .build();
  }

  @Override
  public String getKind() {
    return ManifestStoreType.AZURE_REPO;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public StoreConfig cloneInternal() {
    return AzureRepoStore.builder()
        .connectorRef(connectorRef)
        .gitFetchType(gitFetchType)
        .branch(branch)
        .commitId(commitId)
        .paths(paths)
        .folderPath(folderPath)
        .repoName(repoName)
        .build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    AzureRepoStore azureRepoStore = (AzureRepoStore) overrideConfig;
    AzureRepoStore resultantAzureRepoStore = this;
    if (!ParameterField.isNull(azureRepoStore.getConnectorRef())) {
      resultantAzureRepoStore = resultantAzureRepoStore.withConnectorRef(azureRepoStore.getConnectorRef());
    }
    if (!ParameterField.isNull(azureRepoStore.getPaths())) {
      resultantAzureRepoStore = resultantAzureRepoStore.withPaths(azureRepoStore.getPaths());
    }
    if (!ParameterField.isNull(azureRepoStore.getFolderPath())) {
      resultantAzureRepoStore = resultantAzureRepoStore.withFolderPath(azureRepoStore.getFolderPath());
    }
    if (azureRepoStore.getGitFetchType() != null) {
      resultantAzureRepoStore = resultantAzureRepoStore.withGitFetchType(azureRepoStore.getGitFetchType());
    }
    if (!ParameterField.isNull(azureRepoStore.getBranch())) {
      resultantAzureRepoStore = resultantAzureRepoStore.withBranch(azureRepoStore.getBranch()).withCommitId(null);
    }
    if (!ParameterField.isNull(azureRepoStore.getCommitId())) {
      resultantAzureRepoStore = resultantAzureRepoStore.withCommitId(azureRepoStore.getCommitId()).withBranch(null);
    }
    if (!ParameterField.isNull(azureRepoStore.getRepoName())) {
      resultantAzureRepoStore = resultantAzureRepoStore.withRepoName(azureRepoStore.getRepoName());
    }
    return resultantAzureRepoStore;
  }

  @Override
  public Set<String> validateAtRuntime() {
    return StoreConfigHelper.validateGitStoreType(connectorRef, folderPath, paths, branch, commitId, gitFetchType);
  }
}
