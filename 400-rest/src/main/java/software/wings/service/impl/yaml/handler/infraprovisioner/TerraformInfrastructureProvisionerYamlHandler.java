/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.InfrastructureProvisionerType;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformInfrastructureProvisioner.Yaml;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.NameValuePairYamlHandler;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.Utils;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDP)
@TargetModule(HarnessModule._955_CG_YAML)
public class TerraformInfrastructureProvisionerYamlHandler
    extends InfrastructureProvisionerYamlHandler<Yaml, TerraformInfrastructureProvisioner> {
  @Inject SecretManager secretManager;

  @Override
  public Yaml toYaml(TerraformInfrastructureProvisioner bean, String appId) {
    Yaml yaml = Yaml.builder().build();
    bean.setVariables(null);
    super.toYaml(yaml, bean);

    yaml.setType(InfrastructureProvisionerType.TERRAFORM.name());
    yaml.setSourceType(bean.getSourceType());

    if (bean.getSourceType() != null && bean.getSourceType().equals(TerraformSourceType.S3)) {
      yaml.setS3URI(bean.getS3URI());
      yaml.setAwsSourceConfigName(getSourceRepoSettingName(appId, bean.getAwsConfigId()));
    } else {
      yaml.setPath(bean.getPath());
      yaml.setSourceRepoSettingName(getSourceRepoSettingName(appId, bean.getSourceRepoSettingId()));
      yaml.setSourceRepoBranch(bean.getSourceRepoBranch());
      yaml.setCommitId(bean.getCommitId());
      yaml.setRepoName(bean.getRepoName());
    }

    if (isNotEmpty(bean.getKmsId())) {
      SecretManagerConfig secretManagerConfig = secretManager.getSecretManager(bean.getAccountId(), bean.getKmsId());
      if (isNull(secretManagerConfig)) {
        throw new InvalidRequestException("No secret manager found.", USER);
      }
      yaml.setSecretMangerName(secretManagerConfig.getName());
    }

    yaml.setSkipRefreshBeforeApplyingPlan(bean.isSkipRefreshBeforeApplyingPlan());
    return yaml;
  }

  private List<software.wings.beans.NameValuePair.Yaml> getSortedNameValuePairYamlList(
      List<NameValuePair> nameValuePairList, String appId) {
    NameValuePairYamlHandler nameValuePairYamlHandler = getNameValuePairYamlHandler();
    List<NameValuePair.Yaml> nvpYamlList =
        nameValuePairList.stream()
            .map(nameValuePair -> nameValuePairYamlHandler.toYaml(nameValuePair, appId))
            .collect(toList());
    return Utils.getSortedNameValuePairYamlList(nvpYamlList);
  }

  @Override
  public TerraformInfrastructureProvisioner upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("Couldn't retrieve app from yaml:" + yamlFilePath, appId, USER);

    TerraformInfrastructureProvisioner current = TerraformInfrastructureProvisioner.builder().build();
    toBean(current, changeContext, appId);

    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());
    TerraformInfrastructureProvisioner previous =
        (TerraformInfrastructureProvisioner) infrastructureProvisionerService.getByName(appId, name);

    if (previous != null) {
      current.setUuid(previous.getUuid());
      current.setSyncFromGit(changeContext.getChange().isSyncFromGit());
      current = (TerraformInfrastructureProvisioner) infrastructureProvisionerService.update(current);
    } else {
      current = (TerraformInfrastructureProvisioner) infrastructureProvisionerService.save(current);
    }

    changeContext.setEntity(current);
    return current;
  }

  private void toBean(TerraformInfrastructureProvisioner bean,
      ChangeContext<TerraformInfrastructureProvisioner.Yaml> changeContext, String appId) {
    TerraformInfrastructureProvisioner.Yaml yaml = changeContext.getYaml();
    String yamlFilePath = changeContext.getChange().getFilePath();
    yaml.setVariables(null);
    super.toBean(changeContext, bean, appId, yamlFilePath);
    bean.setPath(yaml.getPath());

    if (yaml.getSourceType() != null && yaml.getSourceType().equals(TerraformSourceType.S3)) {
      validateS3Source(yaml.getAwsSourceConfigName(), yaml.getS3URI(), yaml.getSourceRepoSettingName());
      bean.setAwsConfigId(getSourceRepoSettingId(appId, yaml.getAwsSourceConfigName()));
      bean.setS3URI(yaml.getS3URI());
      bean.setSourceType(TerraformSourceType.S3);
    } else {
      bean.setSourceRepoSettingId(getSourceRepoSettingId(appId, yaml.getSourceRepoSettingName()));
      validateBranchCommitId(yaml.getSourceRepoBranch(), yaml.getCommitId());
      bean.setSourceRepoBranch(yaml.getSourceRepoBranch());
      bean.setCommitId(yaml.getCommitId());
      bean.setRepoName(yaml.getRepoName());
      bean.setSourceType(TerraformSourceType.GIT);
    }

    bean.setSkipRefreshBeforeApplyingPlan(yaml.isSkipRefreshBeforeApplyingPlan());

    if (isNotEmpty(yaml.getSecretMangerName())) {
      SecretManagerConfig secretManagerConfig =
          secretManager.getSecretManagerByName(bean.getAccountId(), yaml.getSecretMangerName());
      if (isNull(secretManagerConfig)) {
        throw new InvalidRequestException(
            format("No secret manager found with name: %s.", yaml.getSecretMangerName()), USER);
      }
      bean.setKmsId(secretManagerConfig.getUuid());
    }
  }

  private List<NameValuePair> getNameValuePairList(List<NameValuePair.Yaml> nameValuePairList) {
    return nameValuePairList.stream()
        .map(nvpYaml
            -> NameValuePair.builder()
                   .name(nvpYaml.getName())
                   .value(nvpYaml.getValue())
                   .valueType(nvpYaml.getValueType())
                   .build())
        .collect(toList());
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  public TerraformInfrastructureProvisioner get(String accountId, String yamlFilePath) {
    return (TerraformInfrastructureProvisioner) yamlHelper.getInfrastructureProvisioner(accountId, yamlFilePath);
  }
}
