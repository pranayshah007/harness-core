/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.Application;
import software.wings.beans.BlueprintProperty;
import software.wings.beans.InfrastructureMappingBlueprint;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisioner.InfraProvisionerYaml;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.NameValuePairYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.Utils;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(CDP)
@TargetModule(HarnessModule._955_CG_YAML)
public abstract class InfrastructureProvisionerYamlHandler<Y extends InfraProvisionerYaml, B
                                                               extends InfrastructureProvisioner>
    extends BaseYamlHandler<Y, B> {
  @Inject YamlHelper yamlHelper;
  @Inject InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject ServiceResourceService serviceResourceService;
  @Inject YamlHandlerFactory yamlHandlerFactory;
  @Inject private AppService appService;
  @Inject SettingsService settingsService;

  protected NameValuePairYamlHandler getNameValuePairYamlHandler() {
    return yamlHandlerFactory.getYamlHandler(YamlType.NAME_VALUE_PAIR);
  }

  public InfraProvisionerYaml toYaml(InfraProvisionerYaml yaml, InfrastructureProvisioner bean) {
    yaml.setDescription(bean.getDescription());
    yaml.setHarnessApiVersion(getHarnessApiVersion());

    if (isNotEmpty(bean.getVariables())) {
      NameValuePairYamlHandler nameValuePairYamlHandler = getNameValuePairYamlHandler();
      List<NameValuePair.Yaml> nvpYamlList =
          bean.getVariables()
              .stream()
              .map(nameValuePair -> nameValuePairYamlHandler.toYaml(nameValuePair, bean.getAppId()))
              .collect(toList());

      yaml.setVariables(Utils.getSortedNameValuePairYamlList(nvpYamlList));
    }

    updateYamlWithAdditionalInfo(bean, bean.getAppId(), yaml);

    return yaml;
  }

  protected String getServiceId(String appId, String serviceName) {
    Service service = serviceResourceService.getServiceByName(appId, serviceName);
    notNullCheck("Invalid Service:" + serviceName, service, USER);
    return service.getUuid();
  }

  protected String getServiceName(String appId, String serviceId) {
    Service service = serviceResourceService.get(appId, serviceId);
    notNullCheck("Invalid Service:" + serviceId, service, USER);
    return service.getName();
  }

  protected InfrastructureMappingBlueprint mapBluePrintYamlToBean(
      InfrastructureMappingBlueprint.Yaml yaml, String appId, String yamlFilePath) {
    String serviceId = getServiceId(appId, yaml.getServiceName());
    notNullCheck("Couldn't retrieve service from yaml:" + yamlFilePath, serviceId, USER);

    List<BlueprintProperty> blueprintProperties = null;
    if (isNotEmpty(yaml.getProperties())) {
      blueprintProperties =
          yaml.getProperties()
              .stream()
              .map(yamlProperty -> {
                BlueprintProperty blueprintProperty = BlueprintProperty.builder()
                                                          .name(yamlProperty.getName())
                                                          .value(yamlProperty.getValue())
                                                          .valueType(yamlProperty.getValueType())
                                                          .build();
                if (isNotEmpty(yamlProperty.getFields())) {
                  List<NameValuePair> fields =
                      yamlProperty.getFields()
                          .stream()
                          .map(nvpYaml
                              -> NameValuePair.builder().name(nvpYaml.getName()).value(nvpYaml.getValue()).build())
                          .collect(toList());
                  blueprintProperty.setFields(fields);
                }
                return blueprintProperty;
              })
              .collect(Collectors.toList());
    }

    return InfrastructureMappingBlueprint.builder()
        .cloudProviderType(yaml.getCloudProviderType())
        .deploymentType(yaml.getDeploymentType())
        .nodeFilteringType(yaml.getNodeFilteringType())
        .serviceId(serviceId)
        .properties(blueprintProperties)
        .build();
  }

  @Override
  public Class getYamlClass() {
    return InfraProvisionerYaml.class;
  }

  public void toBean(ChangeContext<Y> changeContext, B bean, String appId, String yamlPath) {
    Y yaml = changeContext.getYaml();

    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());
    bean.setAppId(appId);
    bean.setName(name);
    bean.setDescription(yaml.getDescription());

    bean.setAccountId(appService.getAccountIdByAppId(appId));

    if (isNotEmpty(yaml.getVariables())) {
      List<NameValuePair> nameValuePairList = yaml.getVariables()
                                                  .stream()
                                                  .map(nvpYaml
                                                      -> NameValuePair.builder()
                                                             .name(nvpYaml.getName())
                                                             .value(nvpYaml.getValue())
                                                             .valueType(nvpYaml.getValueType())
                                                             .build())
                                                  .collect(toList());

      bean.setVariables(nameValuePairList);
    }
  }

  @Override
  public void delete(ChangeContext<Y> changeContext) {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    Optional<Application> optionalApplication = yamlHelper.getApplicationIfPresent(accountId, yamlFilePath);
    if (!optionalApplication.isPresent()) {
      return;
    }

    InfrastructureProvisioner infrastructureProvisioner =
        yamlHelper.getInfrastructureProvisionerByAppIdYamlPath(optionalApplication.get().getUuid(), yamlFilePath);
    if (infrastructureProvisioner != null) {
      infrastructureProvisionerService.delete(optionalApplication.get().getUuid(), infrastructureProvisioner.getUuid(),
          changeContext.getChange().isSyncFromGit());
    }
  }

  protected String getSourceRepoSettingId(String appId, String sourceRepoSettingName) {
    Application application = appService.get(appId);

    SettingAttribute settingAttribute =
        settingsService.getSettingAttributeByName(application.getAccountId(), sourceRepoSettingName);
    notNullCheck("Invalid Source Repo Setting:" + sourceRepoSettingName, settingAttribute, USER);
    return settingAttribute.getUuid();
  }

  protected String getSourceRepoSettingName(String appId, String sourceRepoSettingId) {
    SettingAttribute settingAttribute = settingsService.get(GLOBAL_APP_ID, sourceRepoSettingId);
    notNullCheck("Invalid Source Repo Setting:" + sourceRepoSettingId, settingAttribute, USER);
    return settingAttribute.getName();
  }

  protected void validateBranchCommitId(String sourceRepoBranch, String commitId) {
    if (isEmpty(sourceRepoBranch) && isEmpty(commitId)) {
      throw new InvalidRequestException("Either sourceRepoBranch or commitId should be specified", USER);
    }
    if (isNotEmpty(sourceRepoBranch) && isNotEmpty(commitId)) {
      throw new InvalidRequestException("Cannot specify both sourceRepoBranch and commitId", USER);
    }
  }

  protected void validateS3Source(String awsSourceConfigName, String s3URI, String sourceRepoName) {
    if (isNotEmpty(awsSourceConfigName) && isNotEmpty(s3URI) && (isEmpty(sourceRepoName))) {
      return;
    }
    if (isEmpty(awsSourceConfigName) || isEmpty(s3URI)) {
      throw new InvalidRequestException("Both AWS Cloud Provider and S3 URI must be specified", USER);
    }
    if (isNotEmpty(sourceRepoName) && isNotEmpty(awsSourceConfigName)) {
      throw new InvalidRequestException("Cannot specify both AWS Cloud Provider and GIT repo", USER);
    }
  }
}
