/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static software.wings.graphql.datafetcher.cloudProvider.CloudProviderController.checkIfInputIsNotPresent;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.CGConstants;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.cloudProvider.QLCreateCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLCreateCloudProviderPayload;
import software.wings.graphql.schema.mutation.cloudProvider.QLCreateCloudProviderPayload.QLCreateCloudProviderPayloadBuilder;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class CreateCloudProviderDataFetcher
    extends BaseMutatorDataFetcher<QLCreateCloudProviderInput, QLCreateCloudProviderPayload> {
  @Inject private SettingsService settingsService;
  @Inject private SettingServiceHelper settingServiceHelper;

  @Inject private PcfDataFetcherHelper pcfDataFetcherHelper;
  @Inject private SpotInstDataFetcherHelper spotInstDataFetcherHelper;
  @Inject private GcpDataFetcherHelper gcpDataFetcherHelper;
  @Inject private K8sDataFetcherHelper k8sDataFetcherHelper;
  @Inject private PhysicalDataCenterDataFetcherHelper physicalDataCenterDataFetcherHelper;
  @Inject private AzureDataFetcherHelper azureDataFetcherHelper;
  @Inject private AwsDataFetcherHelper awsDataFetcherHelper;
  @Inject private RancherDataFetcherHelper rancherDataFetcherHelper;

  @Inject
  public CreateCloudProviderDataFetcher() {
    super(QLCreateCloudProviderInput.class, QLCreateCloudProviderPayload.class);
  }

  @Override
  @AuthRule(permissionType = MANAGE_CLOUD_PROVIDERS)
  public QLCreateCloudProviderPayload mutateAndFetch(
      QLCreateCloudProviderInput input, MutationContext mutationContext) {
    QLCreateCloudProviderPayloadBuilder builder =
        QLCreateCloudProviderPayload.builder().clientMutationId(input.getClientMutationId());

    if (input.getCloudProviderType() == null) {
      throw new InvalidRequestException("Invalid cloudProviderType provided in the request");
    }

    SettingAttribute settingAttribute;
    switch (input.getCloudProviderType()) {
      case PCF:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getPcfCloudProvider());
        settingAttribute =
            pcfDataFetcherHelper.toSettingAttribute(input.getPcfCloudProvider(), mutationContext.getAccountId());
        break;
      case SPOT_INST:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getSpotInstCloudProvider());
        settingAttribute = spotInstDataFetcherHelper.toSettingAttribute(
            input.getSpotInstCloudProvider(), mutationContext.getAccountId());
        break;
      case GCP:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getGcpCloudProvider());
        settingAttribute =
            gcpDataFetcherHelper.toSettingAttribute(input.getGcpCloudProvider(), mutationContext.getAccountId());
        break;
      case KUBERNETES_CLUSTER:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getK8sCloudProvider());
        settingAttribute =
            k8sDataFetcherHelper.toSettingAttribute(input.getK8sCloudProvider(), mutationContext.getAccountId());
        break;
      case PHYSICAL_DATA_CENTER:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getPhysicalDataCenterCloudProvider());
        settingAttribute = physicalDataCenterDataFetcherHelper.toSettingAttribute(
            input.getPhysicalDataCenterCloudProvider(), mutationContext.getAccountId());
        break;
      case AZURE:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getAzureCloudProvider());
        settingAttribute =
            azureDataFetcherHelper.toSettingAttribute(input.getAzureCloudProvider(), mutationContext.getAccountId());
        break;
      case AWS:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getAwsCloudProvider());
        settingAttribute =
            awsDataFetcherHelper.toSettingAttribute(input.getAwsCloudProvider(), mutationContext.getAccountId());
        break;
      case RANCHER:
        checkIfInputIsNotPresent(input.getCloudProviderType(), input.getRancherCloudProvider());
        settingAttribute = rancherDataFetcherHelper.toSettingAttribute(
            input.getRancherCloudProvider(), mutationContext.getAccountId());
        break;
      default:
        throw new InvalidRequestException("Invalid cloud provider Type");
    }

    settingAttribute =
        settingsService.saveWithPruning(settingAttribute, CGConstants.GLOBAL_APP_ID, mutationContext.getAccountId());
    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, false);
    return builder.cloudProvider(CloudProviderController.populateCloudProvider(settingAttribute).build()).build();
  }
}
