/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.InfrastructureProvisionerType.TERRAFORM;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.Trimmed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FilenameUtils;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("TERRAFORM")
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class TerraformInfrastructureProvisioner extends InfrastructureProvisioner implements TerraGroupProvisioners {
  public static final String VARIABLE_KEY = "terraform";
  private String sourceRepoSettingId;
  private TerraformSourceType sourceType = TerraformSourceType.GIT;
  private String awsConfigId;
  private String s3URI;
  /**
   * This could be either a branch or a commit id or any other reference which
   * can be checked out.
   */
  private String sourceRepoBranch;
  private String commitId;
  @Trimmed(message = "repoName should not contain leading and trailing spaces") @Nullable private String repoName;
  private String path;
  private String normalizedPath;
  private List<NameValuePair> backendConfigs;
  private List<NameValuePair> environmentVariables;
  private boolean templatized;
  private List<String> workspaces;
  private String kmsId;

  /**
   * Boolean to indicate if we should skip updating terraform state using refresh command before applying an approved
   * terraform plan
   */
  private boolean skipRefreshBeforeApplyingPlan;

  @Override
  public String variableKey() {
    return VARIABLE_KEY;
  }

  TerraformInfrastructureProvisioner() {
    setInfrastructureProvisionerType(TERRAFORM.name());
  }

  @Builder
  private TerraformInfrastructureProvisioner(String uuid, String appId, String name, String sourceRepoSettingId,
      String sourceRepoBranch, String commitId, String path, List<NameValuePair> variables,
      List<InfrastructureMappingBlueprint> mappingBlueprints, String accountId, String description,
      EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath,
      List<NameValuePair> backendConfigs, String repoName, List<NameValuePair> environmentVariables,
      boolean skipRefreshBeforeApplyingPlan, String kmsId, String s3URI, String awsConfigId,
      TerraformSourceType terraformSourceType) {
    super(name, description, TERRAFORM.name(), variables, mappingBlueprints, accountId, uuid, appId, createdBy,
        createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    setSourceRepoSettingId(sourceRepoSettingId);
    setSourceRepoBranch(sourceRepoBranch);
    setCommitId(commitId);
    setS3URI(s3URI);
    setAwsConfigId(awsConfigId);
    setSourceType(terraformSourceType);
    setPath(path);
    setNormalizedPath(FilenameUtils.normalize(path));
    this.backendConfigs = backendConfigs;
    setRepoName(repoName);
    this.environmentVariables = environmentVariables;
    setKmsId(kmsId);
    this.skipRefreshBeforeApplyingPlan = skipRefreshBeforeApplyingPlan;
  }

  /**
   * The type Yaml.
   */
  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  @JsonPropertyOrder({"type", "harnessApiVersion"})
  public static final class Yaml extends InfraProvisionerYaml {
    private String sourceRepoSettingName;
    private String sourceRepoBranch;
    private String commitId;
    private String path;
    private String normalizedPath;
    private List<NameValuePair.Yaml> backendConfigs;
    private List<NameValuePair.Yaml> environmentVariables;
    private String repoName;
    private String secretMangerName;
    private boolean skipRefreshBeforeApplyingPlan;
    private TerraformSourceType sourceType;

    private String awsSourceConfigName;
    private String s3URI;

    @Builder
    public Yaml(String type, String harnessApiVersion, String description, String infrastructureProvisionerType,
        List<NameValuePair.Yaml> variables, List<InfrastructureMappingBlueprint.Yaml> mappingBlueprints,
        String sourceRepoSettingName, String sourceRepoBranch, String path, List<NameValuePair.Yaml> backendConfigs,
        String repoName, List<NameValuePair.Yaml> environmentVariables, String commitId,
        boolean skipRefreshBeforeApplyingPlan, String secretMangerName, String s3URI, TerraformSourceType sourceType,
        String awsSourceConfigName) {
      super(type, harnessApiVersion, description, infrastructureProvisionerType, variables, mappingBlueprints);
      this.sourceRepoSettingName = sourceRepoSettingName;
      this.sourceRepoBranch = sourceRepoBranch;
      this.commitId = commitId;
      this.path = path;
      this.normalizedPath = FilenameUtils.normalize(path);
      this.backendConfigs = backendConfigs;
      this.repoName = repoName;
      this.environmentVariables = environmentVariables;
      this.secretMangerName = secretMangerName;
      this.skipRefreshBeforeApplyingPlan = skipRefreshBeforeApplyingPlan;
      this.s3URI = s3URI;
      this.sourceType = sourceType;
      this.awsSourceConfigName = awsSourceConfigName;
    }
  }
}
