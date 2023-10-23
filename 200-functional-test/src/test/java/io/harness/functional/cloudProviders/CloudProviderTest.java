/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.functional.cloudProviders;

import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.NATARAJA;
import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.FunctionalTests;
import io.harness.functional.AbstractFunctionalTest;
import io.harness.generator.SecretGenerator;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.testframework.framework.Retry;
import io.harness.testframework.framework.matchers.BooleanMatcher;
import io.harness.testframework.restutils.SettingsUtils;

import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.PhysicalDataCenterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import io.restassured.path.json.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@OwnedBy(HarnessTeam.CDP)
public class CloudProviderTest extends AbstractFunctionalTest {
  @Inject private SecretGenerator secretGenerator;
  // Test Constants
  private static String CONNECTOR_NAME = "%s-Automation-CloudProvider-" + System.currentTimeMillis();
  private static String CATEGORY = "CLOUD_PROVIDER";
  private static String AWS_NAMESPACE = "AWS";
  private static String GCP_NAMESPACE = "GCP";
  private static String AZURE_NAMESPACE = "Azure";
  private static String PHYSICAL_DATACENTER_NAMESPACE = "Physical-DataCenter";
  private static String MODIFIED_SUFFIX = "update";

  private static String AWSCloudProviderId;
  private static String GCPCloudProviderId;
  private static String AzureCloudProviderId;
  private static String PhysicalDataCenterCloudProviderId;
  private static final Retry retry = new Retry(10, 1000);
  private static final BooleanMatcher booleanMatcher = new BooleanMatcher();

  @Test
  @Owner(developers = NATARAJA)
  @Category(FunctionalTests.class)
  public void listCloudProviders() {
    JsonPath cloudProviders = SettingsUtils.listCloudproviderConnector(bearerToken, getAccount().getUuid(), CATEGORY);
    assertThat(cloudProviders).isNotNull();
  }

  @Test
  @Owner(developers = NATARAJA, intermittent = true)
  @Category(FunctionalTests.class)
  public void runAWSCloudProviderCRUDTests() {
    retry.executeWithRetry(this::createAWSCloudProvider, booleanMatcher, true);
    log.info("Created AWS Cloud provider with id {}", AWSCloudProviderId);
    updateAWSCloudProvider();
    log.info("Updated AWS Cloud provider with id {}", AWSCloudProviderId);
    deleteAWSCloudProvider();
    log.info("Deleted AWS Cloud provider with id {}", AWSCloudProviderId);
  }

  @Test
  @Owner(developers = DEEPAK, intermittent = true)
  @Category(FunctionalTests.class)
  public void runAzureCloudProviderCRUDTests() {
    // TODO: this test always fails in jenkins but passes in local. Fix this test and uncomment.
    //    retry.executeWithRetry(this::createAzureCloudProvider, booleanMatcher, true);
    //    log.info("Created Azure Cloud provider with id {}", AzureCloudProviderId);
    //    updateAzureCloudProvider();
    //    log.info("Updated Azure Cloud provider with id {}", AzureCloudProviderId);
    //    deleteAzureCloudProvider();
    //    log.info("Deleted Azure Cloud provider with id {}", AzureCloudProviderId);
  }

  @Test
  @Owner(developers = UTKARSH, intermittent = true)
  @Category(FunctionalTests.class)
  public void runGCPCloudProviderCRUDTests() {
    // TODO: this test always fails in jenkins but passes in local. Fix this test and uncomment.
    // retry.executeWithRetry(this ::createGCPCloudProvider, booleanMatcher, true);
    // log.info("Created GCP Cloud provider with id {}", GCPCloudProviderId);
    // retry.executeWithRetry(this ::updateGCPCloudProvider, booleanMatcher, true);
    // log.info("Updated GCP Cloud provider with id {}", GCPCloudProviderId);
    // deleteGCPCloudProvider();
    // log.info("Deleted GCP Cloud provider with id {}", GCPCloudProviderId);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(FunctionalTests.class)
  @Ignore("Functional Flakiness fixing. Needs to be fixed")
  public void runPhysicalDataCenterCloudProvider() {
    retry.executeWithRetry(this::createPhysicalDataCenterCloudProvider, booleanMatcher, true);
    log.info(
        String.format("Created Physical Data Center Cloud provider with id %s", PhysicalDataCenterCloudProviderId));
    updatePhyscialDataCenterCloudProvider();
    log.info(
        String.format("Created Physical Data Center Cloud provider with id %s", PhysicalDataCenterCloudProviderId));
    deletePhysicalDataCenterCloudProvider();
    log.info(
        String.format("Created Physical Data Center Cloud provider with id %s", PhysicalDataCenterCloudProviderId));
  }

  private boolean createAzureCloudProvider() {
    String AZURE_CONNECTOR_NAME = String.format(CONNECTOR_NAME, AZURE_NAMESPACE);
    final String azure_key =
        secretGenerator.ensureStored(getAccount().getUuid(), SecretName.builder().value("azure_key").build());
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withName(AZURE_CONNECTOR_NAME)
            .withAccountId(getAccount().getUuid())
            .withValue(AzureConfig.builder()
                           .clientId(new ScmSecret().decryptToString(new SecretName("azure_client_id")))
                           .tenantId(new ScmSecret().decryptToString(new SecretName("azure_tenant_id")))
                           .key(azure_key.toCharArray())
                           .accountId(getAccount().getUuid())
                           .build())
            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, getAccount().getUuid(), settingAttribute);
    assertThat(setAttrResponse).isNotNull();
    // System.out.println(setAttrResponse.prettyPrint());
    AzureCloudProviderId = setAttrResponse.getString("resource.uuid").trim();

    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, AZURE_CONNECTOR_NAME);
    return connectorFound;
  }

  private boolean createAWSCloudProvider() {
    String AWS_CONNECTOR_NAME = String.format(CONNECTOR_NAME, AWS_NAMESPACE);
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withName(AWS_CONNECTOR_NAME)
            .withAccountId(getAccount().getUuid())
            .withValue(AwsConfig.builder()
                           .accessKey(new ScmSecret().decryptToCharArray(new SecretName("aws_playground_access_key")))
                           .secretKey(new ScmSecret().decryptToCharArray(new SecretName("aws_playground_secret_key")))
                           .accountId(getAccount().getUuid())
                           .build())
            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, getAccount().getUuid(), settingAttribute);
    assertThat(setAttrResponse).isNotNull();
    // System.out.println(setAttrResponse.prettyPrint());
    AWSCloudProviderId = setAttrResponse.getString("resource.uuid").trim();

    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, AWS_CONNECTOR_NAME);
    return connectorFound;
  }

  private boolean createGCPCloudProvider() {
    String GCP_CONNECTOR_NAME = String.format(CONNECTOR_NAME, GCP_NAMESPACE);
    JsonPath setAttrResponse = SettingsUtils.createGCP(bearerToken, getAccount().getUuid(), GCP_CONNECTOR_NAME);
    assertThat(setAttrResponse).isNotNull();
    //    System.out.println(setAttrResponse.prettyPrint());
    GCPCloudProviderId = setAttrResponse.getString("resource.uuid").trim();
    log.info("GCP ConnectorDisconnectHandler created with {}", GCPCloudProviderId);
    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, GCP_CONNECTOR_NAME);
    return connectorFound;
  }

  private boolean createPhysicalDataCenterCloudProvider() {
    String PHYSICAL_DATACENTER_CONNECTOR_NAME = String.format(CONNECTOR_NAME, PHYSICAL_DATACENTER_NAMESPACE);
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withCategory(SettingCategory.CLOUD_PROVIDER)
                                            .withName(PHYSICAL_DATACENTER_CONNECTOR_NAME)
                                            .withAccountId(getAccount().getUuid())
                                            .withValue(PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig()
                                                           .withType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                                                           .build())
                                            .build();

    JsonPath setAttrResponse = SettingsUtils.create(bearerToken, getAccount().getUuid(), settingAttribute);
    assertThat(setAttrResponse).isNotNull();

    // System.out.println(setAttrResponse.prettyPrint());
    PhysicalDataCenterCloudProviderId = setAttrResponse.getString("resource.uuid").trim();

    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, PHYSICAL_DATACENTER_CONNECTOR_NAME);
    return connectorFound;
  }

  private void updateAWSCloudProvider() {
    String AWS_CONNECTOR_NAME = String.format(CONNECTOR_NAME, AWS_NAMESPACE) + MODIFIED_SUFFIX;
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withName(AWS_CONNECTOR_NAME)
            .withAccountId(getAccount().getUuid())
            .withValue(AwsConfig.builder()
                           .accessKey(new ScmSecret().decryptToCharArray(new SecretName("aws_playground_access_key")))
                           .secretKey(new ScmSecret().decryptToCharArray(new SecretName("aws_playground_secret_key")))
                           .accountId(getAccount().getUuid())
                           .build())
            .build();

    JsonPath setAttrResponse =
        SettingsUtils.update(bearerToken, getAccount().getUuid(), settingAttribute, AWSCloudProviderId);
    assertThat(setAttrResponse).isNotNull();

    // Verify cloudprovider is updated i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, AWS_CONNECTOR_NAME);
    assertThat(connectorFound).isTrue();
  }

  private void updateAzureCloudProvider() {
    String AZURE_CONNECTOR_NAME = String.format(CONNECTOR_NAME, AZURE_NAMESPACE) + MODIFIED_SUFFIX;
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withCategory(SettingCategory.CLOUD_PROVIDER)
            .withName(AZURE_CONNECTOR_NAME)
            .withAccountId(getAccount().getUuid())
            .withValue(AzureConfig.builder()
                           .clientId(new ScmSecret().decryptToString(new SecretName("azure_client_id")))
                           .tenantId(new ScmSecret().decryptToString(new SecretName("azure_tenant_id")))
                           .key(new ScmSecret().decryptToCharArray(new SecretName("azure_key")))
                           .accountId(getAccount().getUuid())
                           .build())
            .build();

    JsonPath setAttrResponse =
        SettingsUtils.update(bearerToken, getAccount().getUuid(), settingAttribute, AzureCloudProviderId);
    assertThat(setAttrResponse).isNotNull();

    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, AZURE_CONNECTOR_NAME);
    assertThat(connectorFound).isTrue();
  }

  private void updatePhyscialDataCenterCloudProvider() {
    String PHYSICAL_DATACENTER_CONNECTOR_NAME =
        String.format(CONNECTOR_NAME, PHYSICAL_DATACENTER_NAMESPACE) + MODIFIED_SUFFIX;
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withCategory(SettingCategory.CLOUD_PROVIDER)
                                            .withName(PHYSICAL_DATACENTER_CONNECTOR_NAME)
                                            .withAccountId(getAccount().getUuid())
                                            .withValue(PhysicalDataCenterConfig.Builder.aPhysicalDataCenterConfig()
                                                           .withType(SettingVariableTypes.PHYSICAL_DATA_CENTER.name())
                                                           .build())
                                            .build();

    JsonPath setAttrResponse =
        SettingsUtils.update(bearerToken, getAccount().getUuid(), settingAttribute, PhysicalDataCenterCloudProviderId);
    assertThat(setAttrResponse).isNotNull();
    // System.out.println(setAttrResponse.prettyPrint());

    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, PHYSICAL_DATACENTER_CONNECTOR_NAME);
    assertThat(connectorFound).isTrue();
  }

  private boolean updateGCPCloudProvider() {
    String GCP_CONNECTOR_NAME = String.format(CONNECTOR_NAME, GCP_NAMESPACE) + MODIFIED_SUFFIX;
    log.info("GCP ConnectorDisconnectHandler has id {}", GCPCloudProviderId);
    JsonPath setAttrResponse =
        SettingsUtils.updateGCP(bearerToken, getAccount().getUuid(), GCP_CONNECTOR_NAME, GCPCloudProviderId);
    assertThat(setAttrResponse).isNotNull();
    log.info(setAttrResponse.prettyPrint());
    //        System.out.println(setAttrResponse.prettyPrint());

    // Verify cloudprovider is created i.e cloudprovider with specific name exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, GCP_CONNECTOR_NAME);
    assertThat(connectorFound).isTrue();
    return connectorFound;
  }

  private void deleteAWSCloudProvider() {
    String AWS_CONNECTOR_NAME = String.format(CONNECTOR_NAME, AWS_NAMESPACE) + MODIFIED_SUFFIX;
    SettingsUtils.delete(bearerToken, getAccount().getUuid(), AWSCloudProviderId);

    // Verify cloudprovider is deleted i.e cloudprovider with specific name doesn't exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, AWS_CONNECTOR_NAME);
    assertThat(connectorFound).isFalse();
  }

  private boolean deleteGCPCloudProvider() {
    String GCP_CONNECTOR_NAME = String.format(CONNECTOR_NAME, GCP_NAMESPACE) + MODIFIED_SUFFIX;
    log.info("GCP ConnectorDisconnectHandler has id {}", GCPCloudProviderId);
    SettingsUtils.delete(bearerToken, getAccount().getUuid(), GCPCloudProviderId);
    // Verify cloudprovider is deleted i.e cloudprovider with specific name doesn't exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, GCP_CONNECTOR_NAME);
    assertThat(connectorFound).isFalse();
    return connectorFound;
  }

  private void deleteAzureCloudProvider() {
    String AZURE_CONNECTOR_NAME = String.format(CONNECTOR_NAME, AZURE_NAMESPACE) + MODIFIED_SUFFIX;
    SettingsUtils.delete(bearerToken, getAccount().getUuid(), AzureCloudProviderId);

    // Verify cloudprovider is deleted i.e cloudprovider with specific name doesn't exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, AZURE_CONNECTOR_NAME);
    assertThat(connectorFound).isFalse();
  }

  private void deletePhysicalDataCenterCloudProvider() {
    String PHYSICAL_DATACENTER_CONNECTOR_NAME =
        String.format(CONNECTOR_NAME, PHYSICAL_DATACENTER_NAMESPACE) + MODIFIED_SUFFIX;
    SettingsUtils.delete(bearerToken, getAccount().getUuid(), PhysicalDataCenterCloudProviderId);

    // Verify cloudprovider is deleted i.e cloudprovider with specific name doesn't exist
    boolean connectorFound = SettingsUtils.checkCloudproviderConnectorExist(
        bearerToken, getAccount().getUuid(), CATEGORY, PHYSICAL_DATACENTER_CONNECTOR_NAME);
    assertThat(connectorFound).isFalse();
  }
}
