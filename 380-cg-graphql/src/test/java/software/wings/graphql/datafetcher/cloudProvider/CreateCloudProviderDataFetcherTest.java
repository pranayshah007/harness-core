/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.cloudProvider;

import static io.harness.rule.OwnerRule.IGOR;

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
import software.wings.graphql.schema.mutation.cloudProvider.QLAzureCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLCreateCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLCreateCloudProviderPayload;
import software.wings.graphql.schema.mutation.cloudProvider.QLGcpCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLPcfCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLPhysicalDataCenterCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.QLSpotInstCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.aws.QLAwsCloudProviderInput;
import software.wings.graphql.schema.mutation.cloudProvider.aws.QLEc2IamCredentials;
import software.wings.graphql.schema.mutation.cloudProvider.k8s.QLInheritClusterDetails;
import software.wings.graphql.schema.mutation.cloudProvider.k8s.QLK8sCloudProviderInput;
import software.wings.graphql.schema.type.QLCloudProviderType;
import software.wings.graphql.schema.type.QLEnvFilterType;
import software.wings.graphql.schema.type.QLGenericFilterType;
import software.wings.graphql.schema.type.cloudProvider.QLAwsCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLAzureCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLGcpCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLKubernetesClusterCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLPcfCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLPhysicalDataCenterCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.QLSpotInstCloudProvider;
import software.wings.graphql.schema.type.cloudProvider.aws.QLAwsCredentialsType;
import software.wings.graphql.schema.type.cloudProvider.k8s.QLClusterDetailsType;
import software.wings.graphql.schema.type.secrets.QLAppEnvScope;
import software.wings.graphql.schema.type.secrets.QLAppScopeFilter;
import software.wings.graphql.schema.type.secrets.QLEnvScopeFilter;
import software.wings.graphql.schema.type.secrets.QLUsageScope;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CreateCloudProviderDataFetcherTest extends AbstractDataFetcherTestBase {
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

  @InjectMocks private CreateCloudProviderDataFetcher dataFetcher = new CreateCloudProviderDataFetcher();

  @Before
  public void setup() throws SQLException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createPcf() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(PcfConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(pcfDataFetcherHelper)
        .toSettingAttribute(isA(QLPcfCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLCreateCloudProviderInput.builder()
                                       .cloudProviderType(QLCloudProviderType.PCF)
                                       .pcfCloudProvider(QLPcfCloudProviderInput.builder()
                                                             .name(RequestField.ofNullable("NAME"))
                                                             .endpointUrl(RequestField.ofNullable("URL"))
                                                             .userName(RequestField.ofNullable("USER"))
                                                             .passwordSecretId(RequestField.ofNullable("PASS"))
                                                             .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLPcfCloudProvider.class);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createSpotInst() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withUuid(CLOUD_PROVIDER_ID)
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(SpotInstConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(spotInstDataFetcherHelper)
        .toSettingAttribute(isA(QLSpotInstCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLCreateCloudProviderInput.builder()
            .cloudProviderType(QLCloudProviderType.SPOT_INST)
            .spotInstCloudProvider(QLSpotInstCloudProviderInput.builder()
                                       .name(RequestField.ofNullable("NAME"))
                                       .accountId(RequestField.ofNullable("SpotInstAccountId"))
                                       .tokenSecretId(RequestField.ofNullable("SpotInstToken"))
                                       .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLSpotInstCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createGcp() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withUuid(CLOUD_PROVIDER_ID)
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(GcpConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(gcpDataFetcherHelper)
        .toSettingAttribute(isA(QLGcpCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLCreateCloudProviderInput.builder()
                                       .cloudProviderType(QLCloudProviderType.GCP)
                                       .gcpCloudProvider(QLGcpCloudProviderInput.builder()
                                                             .name(RequestField.ofNullable("NAME"))
                                                             .serviceAccountKeySecretId(RequestField.ofNullable("Key"))
                                                             .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLGcpCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createK8s() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withUuid(CLOUD_PROVIDER_ID)
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(KubernetesClusterConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(k8sDataFetcherHelper)
        .toSettingAttribute(isA(QLK8sCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLCreateCloudProviderInput.builder()
            .cloudProviderType(QLCloudProviderType.KUBERNETES_CLUSTER)
            .k8sCloudProvider(
                QLK8sCloudProviderInput.builder()
                    .name(RequestField.ofNullable("K8S"))
                    .skipValidation(RequestField.ofNullable(Boolean.TRUE))
                    .clusterDetailsType(RequestField.ofNullable(QLClusterDetailsType.INHERIT_CLUSTER_DETAILS))
                    .inheritClusterDetails(RequestField.ofNullable(
                        QLInheritClusterDetails.builder()
                            .delegateSelectors(RequestField.ofNullable(new HashSet<>(Arrays.asList("DELEGATE"))))
                            .build()))
                    .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLKubernetesClusterCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createPhysicalDataCenter() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig()
                                                  .withType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                                                  .build())
                                   .build();

    doReturn(setting)
        .when(physicalDataCenterDataFetcherHelper)
        .toSettingAttribute(isA(QLPhysicalDataCenterCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLCreateCloudProviderInput.builder()
                                       .cloudProviderType(QLCloudProviderType.PHYSICAL_DATA_CENTER)
                                       .physicalDataCenterCloudProvider(QLPhysicalDataCenterCloudProviderInput.builder()
                                                                            .name(RequestField.ofNullable("NAME"))
                                                                            .usageScope(RequestField.ofNull())
                                                                            .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLPhysicalDataCenterCloudProvider.class);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createAzure() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withUuid(CLOUD_PROVIDER_ID)
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(AzureConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(azureDataFetcherHelper)
        .toSettingAttribute(isA(QLAzureCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload =
        dataFetcher.mutateAndFetch(QLCreateCloudProviderInput.builder()
                                       .cloudProviderType(QLCloudProviderType.AZURE)
                                       .azureCloudProvider(QLAzureCloudProviderInput.builder()
                                                               .name(RequestField.ofNullable("AZURE"))
                                                               .clientId(RequestField.ofNullable("CLIENT_ID"))
                                                               .tenantId(RequestField.ofNullable("TENANT_ID"))
                                                               .keySecretId(RequestField.ofNullable("KEY"))
                                                               .build())
                                       .build(),
            MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLAzureCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  @Test
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createAws() {
    SettingAttribute setting = SettingAttribute.Builder.aSettingAttribute()
                                   .withUuid(CLOUD_PROVIDER_ID)
                                   .withCategory(SettingAttribute.SettingCategory.CLOUD_PROVIDER)
                                   .withValue(AwsConfig.builder().accountId(ACCOUNT_ID).build())
                                   .build();

    doReturn(setting)
        .when(awsDataFetcherHelper)
        .toSettingAttribute(isA(QLAwsCloudProviderInput.class), isA(String.class));

    doReturn(setting)
        .when(settingsService)
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));

    doNothing()
        .when(settingServiceHelper)
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    QLCreateCloudProviderPayload payload = dataFetcher.mutateAndFetch(
        QLCreateCloudProviderInput.builder()
            .cloudProviderType(QLCloudProviderType.AWS)
            .awsCloudProvider(
                QLAwsCloudProviderInput.builder()
                    .name(RequestField.ofNullable("AWS"))
                    .credentialsType(RequestField.ofNullable(QLAwsCredentialsType.EC2_IAM))
                    .ec2IamCredentials(RequestField.ofNullable(
                        QLEc2IamCredentials.builder().delegateSelector(RequestField.ofNullable("DELEGATE")).build()))
                    .build())
            .build(),
        MutationContext.builder().accountId(ACCOUNT_ID).build());

    verify(settingsService, times(1))
        .saveWithPruning(isA(SettingAttribute.class), isA(String.class), isA(String.class));
    verify(settingServiceHelper, times(1))
        .updateSettingAttributeBeforeResponse(isA(SettingAttribute.class), isA(Boolean.class));

    assertThat(payload.getCloudProvider()).isNotNull();
    assertThat(payload.getCloudProvider()).isInstanceOf(QLAwsCloudProvider.class);
    assertThat(payload.getCloudProvider().getId()).isEqualTo(CLOUD_PROVIDER_ID);
  }

  public static QLUsageScope usageScope() {
    return QLUsageScope.builder()
        .appEnvScopes(Sets.newHashSet(
            QLAppEnvScope.builder()
                .application(QLAppScopeFilter.builder().filterType(QLGenericFilterType.ALL).build())
                .environment(QLEnvScopeFilter.builder().filterType(QLEnvFilterType.NON_PRODUCTION_ENVIRONMENTS).build())
                .build()))
        .build();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void createWithoutTypeParameter() {
    dataFetcher.mutateAndFetch(
        QLCreateCloudProviderInput.builder().build(), MutationContext.builder().accountId(ACCOUNT_ID).build());
  }
}
