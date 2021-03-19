package software.wings.service.impl.yaml.handler.InfraDefinition;

import static io.harness.validation.Validator.notNullCheck;

import software.wings.beans.InfrastructureType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.infra.AzureWebAppInfra;
import software.wings.infra.AzureWebAppInfraYaml;
import software.wings.service.impl.yaml.handler.CloudProviderInfrastructure.CloudProviderInfrastructureYamlHandler;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;

public class AzureWebAppInfraYamlHandler
    extends CloudProviderInfrastructureYamlHandler<AzureWebAppInfraYaml, AzureWebAppInfra> {
  @Inject private SettingsService settingsService;

  @Override
  public AzureWebAppInfraYaml toYaml(AzureWebAppInfra bean, String appId) {
    String cloudProviderId = bean.getCloudProviderId();
    SettingAttribute cloudProvider = settingsService.get(cloudProviderId);
    notNullCheck(String.format("Cloud provider with id = [%s], does not exist", cloudProviderId), cloudProvider);

    return AzureWebAppInfraYaml.builder()
        .type(InfrastructureType.AZURE_WEBAPP)
        .cloudProviderName(cloudProvider.getName())
        .subscriptionId(bean.getSubscriptionId())
        .resourceGroup(bean.getResourceGroup())
        .build();
  }

  @Override
  public AzureWebAppInfra upsertFromYaml(
      ChangeContext<AzureWebAppInfraYaml> changeContext, List<ChangeContext> changeSetContext) {
    AzureWebAppInfra bean = AzureWebAppInfra.builder().build();
    AzureWebAppInfraYaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    SettingAttribute cloudProvider = settingsService.getSettingAttributeByName(accountId, yaml.getCloudProviderName());
    notNullCheck(String.format("Cloud provider with id = [%s], does not exist", cloudProvider), cloudProvider);

    bean.setCloudProviderId(cloudProvider.getUuid());
    bean.setSubscriptionId(yaml.getSubscriptionId());
    bean.setResourceGroup(yaml.getResourceGroup());
    return bean;
  }

  @Override
  public Class getYamlClass() {
    return AzureWebAppInfraYaml.class;
  }
}
