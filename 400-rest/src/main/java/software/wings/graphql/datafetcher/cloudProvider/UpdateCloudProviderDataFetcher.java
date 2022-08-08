/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.SettingCategory.CLOUD_PROVIDER;
import static software.wings.graphql.datafetcher.cloudProvider.CloudProviderController.checkIfInputIsNotPresent;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateCloudProviderPayload;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateCloudProviderPayload.QLUpdateCloudProviderPayloadBuilder;
import software.wings.graphql.schema.type.QLCloudProviderType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UpdateCloudProviderDataFetcher
    extends BaseMutatorDataFetcher<QLUpdateCloudProviderInput, QLUpdateCloudProviderPayload> {
  @Inject private SettingsService settingsService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private K8sDataFetcherHelper k8sDataFetcherHelper;
  @Inject private GcpDataFetcherHelper gcpDataFetcherHelper;
  @Inject private SpotInstDataFetcherHelper spotInstDataFetcherHelper;
  @Inject private PcfDataFetcherHelper pcfDataFetcherHelper;
  @Inject private PhysicalDataCenterDataFetcherHelper physicalDataCenterDataFetcherHelper;
  @Inject private AzureDataFetcherHelper azureDataFetcherHelper;
  @Inject private AwsDataFetcherHelper awsDataFetcherHelper;
  @Inject private RancherDataFetcherHelper rancherDataFetcherHelper;

  @Inject
  public UpdateCloudProviderDataFetcher() {
    super(QLUpdateCloudProviderInput.class, QLUpdateCloudProviderPayload.class);
  }

  @Override
  @AuthRule(permissionType = MANAGE_CLOUD_PROVIDERS)
  protected QLUpdateCloudProviderPayload mutateAndFetch(
      QLUpdateCloudProviderInput input, MutationContext mutationContext) {
    String cloudProviderId = input.getCloudProviderId();
    String accountId = mutationContext.getAccountId();

    if (isBlank(cloudProviderId)) {
      throw new InvalidRequestException("The cloudProviderId cannot be null");
    }

    if (input.getCloudProviderType() == null) {
      throw new InvalidRequestException("Invalid cloudProviderType provided in the request");
    }

    SettingAttribute settingAttribute = settingsService.getByAccount(accountId, cloudProviderId);

    if (settingAttribute == null || settingAttribute.getValue() == null
        || CLOUD_PROVIDER != settingAttribute.getCategory()) {
      throw new InvalidRequestException(
          String.format("No cloud provider exists with the cloudProviderId %s", cloudProviderId));
    }

    checkIfCloudProviderTypeMatchesTheInputType(settingAttribute.getValue().getType(), input.getCloudProviderType());
    QLUpdateCloudProviderPayloadBuilder builder =
        QLUpdateCloudProviderPayload.builder().clientMutationId(input.getClientMutationId());

    switch (input.getCloudProviderType()) {
      case PCF:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getPcfCloudProvider());
        pcfDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getPcfCloudProvider(), mutationContext.getAccountId());
        break;
      case SPOT_INST:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getSpotInstCloudProvider());
        spotInstDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getSpotInstCloudProvider(), mutationContext.getAccountId());
        break;
      case GCP:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getGcpCloudProvider());
        gcpDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getGcpCloudProvider(), mutationContext.getAccountId());
        break;
      case KUBERNETES_CLUSTER:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getK8sCloudProvider());
        k8sDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getK8sCloudProvider(), mutationContext.getAccountId());
        break;
      case PHYSICAL_DATA_CENTER:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getPhysicalDataCenterCloudProvider());
        physicalDataCenterDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getPhysicalDataCenterCloudProvider(), mutationContext.getAccountId());
        break;
      case AZURE:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getAzureCloudProvider());
        azureDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getAzureCloudProvider(), mutationContext.getAccountId());
        break;
      case AWS:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getAwsCloudProvider());
        awsDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getAwsCloudProvider(), mutationContext.getAccountId());
        break;
      case RANCHER:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getRancherCloudProvider());
        rancherDataFetcherHelper.updateSettingAttribute(
            settingAttribute, input.getRancherCloudProvider(), mutationContext.getAccountId());
        break;
      default:
        throw new InvalidRequestException("Invalid cloud provider type");
    }
    settingAttribute =
        settingsService.updateWithSettingFields(settingAttribute, settingAttribute.getUuid(), GLOBAL_APP_ID);
    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, false);
    return builder.cloudProvider(CloudProviderController.populateCloudProvider(settingAttribute).build()).build();
  }

  private void checkIfCloudProviderTypeMatchesTheInputType(
      String settingVariableType, QLCloudProviderType cloudProviderType) {
    if (!settingVariableType.equals(cloudProviderType.toString())) {
      throw new InvalidRequestException(String.format(
          "The existing cloud provider is of type %s and the update operation inputs a cloud proivder of type %s",
          settingVariableType, cloudProviderType));
    }
  }
}
