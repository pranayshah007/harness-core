/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static io.harness.rule.OwnerRule.IGOR;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.utils.RequestField;

import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.PcfConfig;
import software.wings.beans.PhysicalDataCenterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateAzureCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateCloudProviderPayload;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateGcpCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdatePcfCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdatePhysicalDataCenterCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLUpdateSpotInstCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.aws.QLUpdateAwsCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.aws.QLUpdateEc2IamCredentials;
import software.wings.graphql.schema.mutation.cloudProvider.k8s.QLUpdateInheritClusterDetails;
import software.wings.graphql.schema.mutation.cloudProvider.k8s.QLUpdateK8sCloudProviderInput;
import software.wings.graphql.schema.type.QLCloudProviderType;
import software.wings.graphql.schema.type.cloudProvider.QLAwsCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLAzureCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLGcpCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLKubernetesClusterCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLPcfCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLPhysicalDataCenterCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLSpotInstCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.aws.QLAwsCredentialsType;
import software.wings.graphql.schema.type.cloudProvider.k8s.QLClusterDetailsType;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UpdateCloudProviderDataFetcherTest extends AbstractDataFetcherTestBase {
  private static final String CLOUD_PROVIDER_ID = "CP-ID";
  private static final String ACCOUNT_ID = "ACCOUNT-ID";

  @Mock private SettingsService settingsService;
  @Mock private SettingServiceHelper settingServiceHelper;
  @Mock private PcfDataFetcherHelper pcfDataFetcherHelper;
  @Mock private SpotInstDataFetcherHelper spotInstDataFetcherHelper;
  @Mock private GcpDataFetcherHelper gcpDataFetcherHelper;
  @Mock private K8sDataFetcherHelper k8sDataFetcherHelper;
  @Mock private PhysicalDataCenterDataFetcherHelper physicalDataCenterDataFetcherHelper;
  @Mock private AzureDataFetcherHelper azureDataFetcherHelper;
  @Mock private AwsDataFetcherHelper awsDataFetcherHelper;

  @InjectMocks @Inject private UpdateCloudProviderDataFetcher dataFetcher;

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updatePcf() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(PcfConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doNothing()
        .when(pcfDataFetcherHelper)
        .updateSettingAttribute(
            isA(SettingAttribute.class), isA(QLUpdatePcfCloudProviderInput.class), isA(String.class));

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(PcfConfig.builder().accountId(ACCOUNT_ID).build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLUpdateCloudProviderInput.builder()
                                       .cloudProviderId(CLOUD_PROVIDER_ID)
                                       .cloudProviderType(QLCloudProviderType.PCF)
                                       .pcfCloudProvider(QLUpdatePcfCloudProviderInput.builder()
                                                             .name(RequestField.ofNullable("NAME"))
                                                             .endpointUrl(RequestField.ofNullable("URL"))
                                                             .userName(RequestField.ofNullable("USER"))
                                                             .passwordSecretId(RequestField.ofNullable("PASS"))
                                                             .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLPcfCloudProvider.class);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateSpotInst() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(SpotInstConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withUuid(CLOUD_PROVIDER_ID)
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(SpotInstConfig.builder().accountId(ACCOUNT_ID).build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLUpdateCloudProviderInput.builder()
            .cloudProviderId(CLOUD_PROVIDER_ID)
            .cloudProviderType(QLCloudProviderType.SPOT_INST)
            .spotInstCloudProvider(QLUpdateSpotInstCloudProviderInput.builder()
                                       .name(RequestField.ofNullable("NAME"))
                                       .accountId(RequestField.ofNullable("SpotInstAccountId"))
                                       .tokenSecretId(RequestField.ofNullable("SpotInstToken"))
                                       .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLSpotInstCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateGcp() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(GcpConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withUuid(CLOUD_PROVIDER_ID)
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(GcpConfig.builder().accountId(ACCOUNT_ID).build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLUpdateCloudProviderInput.builder()
                                       .cloudProviderId(CLOUD_PROVIDER_ID)
                                       .cloudProviderType(QLCloudProviderType.GCP)
                                       .gcpCloudProvider(QLUpdateGcpCloudProviderInput.builder()
                                                             .name(RequestField.ofNullable("NAME"))
                                                             .serviceAccountKeySecretId(RequestField.ofNullable("Key"))
                                                             .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLGcpCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateK8s() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(KubernetesClusterConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withUuid(CLOUD_PROVIDER_ID)
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(KubernetesClusterConfig.builder().accountId(ACCOUNT_ID).build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLUpdateCloudProviderInput.builder()
            .cloudProviderId(CLOUD_PROVIDER_ID)
            .cloudProviderType(QLCloudProviderType.KUBERNETES_CLUSTER)
            .k8sCloudProvider(
                QLUpdateK8sCloudProviderInput.builder()
                    .name(RequestField.ofNullable("K8S"))
                    .skipValidation(RequestField.ofNullable(Boolean.TRUE))
                    .clusterDetailsType(RequestField.ofNullable(QLClusterDetailsType.INHERIT_CLUSTER_DETAILS))
                    .inheritClusterDetails(
                        RequestField.ofNullable(QLUpdateInheritClusterDetails.builder()
                                                    .delegateSelectors(RequestField.ofNullable(
                                                        new HashSet<>(Collections.singletonList("DELEGATE"))))
                                                    .build()))
                    .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLKubernetesClusterCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updatePhysicalDataCenter() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig()
                                                  .withType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                                                  .build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doNothing()
        .when(physicalDataCenterDataFetcherHelper)
        .updateSettingAttribute(
            isA(SettingAttribute.class), isA(QLUpdatePhysicalDataCenterCloudProviderInput.class), isA(String.class));

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig()
                                .withType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                                .build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLUpdateCloudProviderInput.builder()
            .cloudProviderId(CLOUD_PROVIDER_ID)
            .cloudProviderType(QLCloudProviderType.PHYSICAL_DATA_CENTER)
            .physicalDataCenterCloudProvider(QLUpdatePhysicalDataCenterCloudProviderInput.builder()
                                                 .name(RequestField.ofNullable("NAME"))
                                                 .usageScope(RequestField.ofNull())
                                                 .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLPhysicalDataCenterCloudProvider.class);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateAzure() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(AzureConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withUuid(CLOUD_PROVIDER_ID)
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(AzureConfig.builder().accountId(ACCOUNT_ID).build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLUpdateCloudProviderInput.builder()
                                       .cloudProviderId(CLOUD_PROVIDER_ID)
                                       .cloudProviderType(QLCloudProviderType.AZURE)
                                       .azureCloudProvider(QLUpdateAzureCloudProviderInput.builder()
                                                               .name(RequestField.ofNullable("Azure"))
                                                               .clientId(RequestField.ofNullable("CLIENT_ID"))
                                                               .tenantId(RequestField.ofNullable("TENANT_ID"))
                                                               .keySecretId(RequestField.ofNullable("KEY"))
                                                               .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLAzureCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateAws() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(AwsConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withUuid(CLOUD_PROVIDER_ID)
                 .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                 .withValue(AwsConfig.builder().accountId(ACCOUNT_ID).build())
                 .build())
        .when(settingsService)
        .updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLUpdateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLUpdateCloudProviderInput.builder()
            .cloudProviderId(CLOUD_PROVIDER_ID)
            .cloudProviderType(QLCloudProviderType.AWS)
            .awsCloudProvider(QLUpdateAwsCloudProviderInput.builder()
                                  .name(RequestField.ofNullable("AWS"))
                                  .credentialsType(RequestField.ofNullable(QLAwsCredentialsType.EC2_IAM))
                                  .ec2IamCredentials(
                                      RequestField.ofNullable(QLUpdateEc2IamCredentials.builder()
                                                                  .delegateSelector(RequestField.ofNullable("DELEGATE"))
                                                                  .build()))
                                  .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1)).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);
    verify(settingsService, times(1)).updateWithSettingFields(setting, setting.getUuid(), GLOBAL_APP_ID);
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLAwsCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateOfNonExistingSetting() {
    doReturn(null).when(settingsService).getByAccount(ACCOUNT_ID, CLOUD_PROVIDER_ID);

    dataFetcher.mutateAndFetch(QLUpdateCloudProviderInput.builder()
                                   .cloudProviderId(CLOUD_PROVIDER_ID)
                                   .cloudProviderType(QLCloudProviderType.PCF)
                                   .pcfCloudProvider(QLUpdatePcfCloudProviderInput.builder()
                                                         .name(RequestField.ofNullable("NAME"))
                                                         .endpointUrl(RequestField.ofNullable("URL"))
                                                         .userName(RequestField.ofNullable("USER"))
                                                         .passwordSecretId(RequestField.ofNullable("PASS"))
                                                         .build())
                                   .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateWithoutIdParameter() {
    dataFetcher.mutateAndFetch(QLUpdateCloudProviderInput.builder().cloudProviderType(QLCloudProviderType.PCF).build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void updateWithoutTypeParameter() {
    dataFetcher.mutateAndFetch(QLUpdateCloudProviderInput.builder().cloudProviderId(CLOUD_PROVIDER_ID).build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());
  }
}
