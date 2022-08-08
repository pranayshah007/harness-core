/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector.types;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.utils.RequestField;

import software.wings.beans.SettingAttribute;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.beans.settings.helm.OciHelmRepoConfig;
import software.wings.graphql.datafetcher.connector.ConnectorsController;
import software.wings.graphql.schema.mutation.connector.input.QLConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.QLUpdateConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLAmazonS3PlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLGCSPlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHelmConnectorInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLHttpServerPlatformInput;
import software.wings.graphql.schema.mutation.connector.input.helm.QLOCIPlatformInput;
import software.wings.graphql.schema.type.QLConnectorType;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;

import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(CDP)
public class HelmConnector extends Connector {
  private FeatureFlagService featureFlagService;
  private SecretManager secretManager;
  private ConnectorsController connectorsController;
  private SettingsService settingsService;

  @Override
  public SettingAttribute getSettingAttribute(QLConnectorInput input, String accountId) {
    QLHelmConnectorInput helmConnectorInput = input.getHelmConnector();
    SettingAttribute settingAttribute;

    QLAmazonS3PlatformInput amazonS3PlatformInput = getAmazonS3PlatformInput(helmConnectorInput);
    QLGCSPlatformInput gcsPlatformInput = getGCSPlatformInput(helmConnectorInput);
    QLHttpServerPlatformInput httpServerPlatformInput = getHttpServerPlatformInput(helmConnectorInput);

    if (amazonS3PlatformInput != null) {
      AmazonS3HelmRepoConfig amazonS3HelmRepoConfig = new AmazonS3HelmRepoConfig();
      amazonS3HelmRepoConfig.setAccountId(accountId);
      setAmazonS3PlatformDetails(amazonS3PlatformInput, amazonS3HelmRepoConfig, accountId);
      settingAttribute = getSettingAttribute(amazonS3HelmRepoConfig, accountId);

    } else if (gcsPlatformInput != null) {
      GCSHelmRepoConfig gcsHelmRepoConfig = new GCSHelmRepoConfig();
      gcsHelmRepoConfig.setAccountId(accountId);
      setGCSPlatformDetails(gcsPlatformInput, gcsHelmRepoConfig, accountId);
      settingAttribute = getSettingAttribute(gcsHelmRepoConfig, accountId);

    } else if (httpServerPlatformInput != null) {
      HttpHelmRepoConfig httpHelmRepoConfig = new HttpHelmRepoConfig();
      httpHelmRepoConfig.setAccountId(accountId);
      setUsernameAndPassword(httpServerPlatformInput, httpHelmRepoConfig);
      setUrl(httpServerPlatformInput, httpHelmRepoConfig);
      settingAttribute = getSettingAttribute(httpHelmRepoConfig, accountId);

    } else if (featureFlagService.isEnabled(FeatureName.HELM_OCI_SUPPORT, accountId)) {
      QLOCIPlatformInput ociPlatformInput = getOCIPlatformInput(helmConnectorInput);
      if (ociPlatformInput == null) {
        throw new InvalidRequestException("Hosting platform details are not specified");
      }
      OciHelmRepoConfig ociHelmRepoConfig = new OciHelmRepoConfig();
      ociHelmRepoConfig.setAccountId(accountId);
      setUsernameAndPassword(ociPlatformInput, ociHelmRepoConfig);
      setUrl(ociPlatformInput, ociHelmRepoConfig);
      settingAttribute = getSettingAttribute(ociHelmRepoConfig, accountId);

    } else {
      throw new InvalidRequestException("Hosting platform details are not specified");
    }

    if (helmConnectorInput.getName().isPresent()) {
      helmConnectorInput.getName().getValue().ifPresent(name -> settingAttribute.setName(name.trim()));
    }

    return settingAttribute;
  }

  @Override
  public void updateSettingAttribute(SettingAttribute settingAttribute, QLUpdateConnectorInput input) {
    QLHelmConnectorInput helmConnectorInput = input.getHelmConnector();

    QLAmazonS3PlatformInput amazonS3PlatformInput = getAmazonS3PlatformInput(helmConnectorInput);
    QLGCSPlatformInput gcsPlatformInput = getGCSPlatformInput(helmConnectorInput);
    QLHttpServerPlatformInput httpServerPlatformInput = getHttpServerPlatformInput(helmConnectorInput);

    if (amazonS3PlatformInput != null) {
      AmazonS3HelmRepoConfig amazonS3HelmRepoConfig = (AmazonS3HelmRepoConfig) settingAttribute.getValue();
      setAmazonS3PlatformDetails(amazonS3PlatformInput, amazonS3HelmRepoConfig, settingAttribute.getAccountId());
      settingAttribute.setValue(amazonS3HelmRepoConfig);

    } else if (gcsPlatformInput != null) {
      GCSHelmRepoConfig gcsHelmRepoConfig = (GCSHelmRepoConfig) settingAttribute.getValue();
      setGCSPlatformDetails(gcsPlatformInput, gcsHelmRepoConfig, settingAttribute.getAccountId());
      settingAttribute.setValue(gcsHelmRepoConfig);

    } else if (httpServerPlatformInput != null) {
      HttpHelmRepoConfig httpHelmRepoConfig = (HttpHelmRepoConfig) settingAttribute.getValue();
      setUsernameAndPassword(httpServerPlatformInput, httpHelmRepoConfig);
      setUrl(httpServerPlatformInput, httpHelmRepoConfig);
      settingAttribute.setValue(httpHelmRepoConfig);

    } else if (featureFlagService.isEnabled(FeatureName.HELM_OCI_SUPPORT, settingAttribute.getAccountId())) {
      QLOCIPlatformInput ociPlatformInput = getOCIPlatformInput(helmConnectorInput);

      if (ociPlatformInput != null) {
        OciHelmRepoConfig ociHelmRepoConfig = (OciHelmRepoConfig) settingAttribute.getValue();
        setUsernameAndPassword(ociPlatformInput, ociHelmRepoConfig);
        setUrl(ociPlatformInput, ociHelmRepoConfig);
        settingAttribute.setValue(ociHelmRepoConfig);
      }
    }

    if (helmConnectorInput.getName().isPresent()) {
      helmConnectorInput.getName().getValue().ifPresent(name -> settingAttribute.setName(name.trim()));
    }
  }

  @Override
  public void checkSecrets(QLConnectorInput input, String accountId) {
    QLHelmConnectorInput helmConnectorInput = input.getHelmConnector();
    checkHelmConnectorSecrets(accountId, helmConnectorInput);
  }

  private void checkHelmConnectorSecrets(String accountId, QLHelmConnectorInput helmConnectorInput) {
    QLHttpServerPlatformInput httpServerPlatformInput = getHttpServerPlatformInput(helmConnectorInput);

    if (httpServerPlatformInput != null) {
      checkUserNameExists(httpServerPlatformInput);

      httpServerPlatformInput.getPasswordSecretId().getValue().ifPresent(
          secretId -> checkSecretExists(secretManager, accountId, secretId));
    } else if (featureFlagService.isEnabled(FeatureName.HELM_OCI_SUPPORT, accountId)) {
      QLOCIPlatformInput ociPlatformInput = getOCIPlatformInput(helmConnectorInput);
      if (ociPlatformInput != null) {
        checkUserNameExists(ociPlatformInput);

        ociPlatformInput.getPasswordSecretId().getValue().ifPresent(
            secretId -> checkSecretExists(secretManager, accountId, secretId));
      }
    }
  }

  @Override
  public void checkSecrets(QLUpdateConnectorInput input, SettingAttribute settingAttribute) {
    QLHelmConnectorInput helmConnectorInput = input.getHelmConnector();
    checkHelmConnectorSecrets(settingAttribute.getAccountId(), helmConnectorInput);
  }

  @Override
  public void checkInputExists(QLConnectorInput input) {
    QLConnectorType type = input.getConnectorType();
    QLHelmConnectorInput helmConnectorInput = input.getHelmConnector();
    checkHelmConnectorExists(type, helmConnectorInput);
  }

  private void checkHelmConnectorExists(QLConnectorType type, QLHelmConnectorInput helmConnectorInput) {
    connectorsController.checkInputExists(type, helmConnectorInput);

    QLAmazonS3PlatformInput amazonS3PlatformInput = getAmazonS3PlatformInput(helmConnectorInput);
    QLGCSPlatformInput gcsPlatformInput = getGCSPlatformInput(helmConnectorInput);
    QLHttpServerPlatformInput httpServerPlatformInput = getHttpServerPlatformInput(helmConnectorInput);
    QLOCIPlatformInput ociPlatformInput = getOCIPlatformInput(helmConnectorInput);

    if (amazonS3PlatformInput == null && gcsPlatformInput == null && httpServerPlatformInput == null
        && ociPlatformInput == null) {
      throw new InvalidRequestException("Hosting platform details should be specified");
    } else if ((amazonS3PlatformInput != null && gcsPlatformInput != null)
        || (gcsPlatformInput != null && httpServerPlatformInput != null)
        || (amazonS3PlatformInput != null && httpServerPlatformInput != null)
        || (amazonS3PlatformInput != null && ociPlatformInput != null)
        || (gcsPlatformInput != null && ociPlatformInput != null)
        || (httpServerPlatformInput != null && ociPlatformInput != null)) {
      throw new InvalidRequestException("Only one hosting platform details should be specified");
    }

    if ((type == QLConnectorType.AMAZON_S3_HELM_REPO && amazonS3PlatformInput == null)
        || (type == QLConnectorType.GCS_HELM_REPO && gcsPlatformInput == null)
        || (type == QLConnectorType.HTTP_HELM_REPO && httpServerPlatformInput == null)
        || (type == QLConnectorType.OCI_HELM_REPO && ociPlatformInput == null)) {
      throw new InvalidRequestException(
          String.format("Wrong hosting platform provided with the request for %s connector", type.getStringValue()));
    }
  }

  @Override
  public void checkInputExists(QLUpdateConnectorInput input) {
    QLConnectorType type = input.getConnectorType();
    QLHelmConnectorInput helmConnectorInput = input.getHelmConnector();
    checkHelmConnectorExists(type, helmConnectorInput);
  }

  private QLAmazonS3PlatformInput getAmazonS3PlatformInput(QLHelmConnectorInput helmConnectorInput) {
    if (helmConnectorInput.getAmazonS3PlatformDetails().isPresent()) {
      return helmConnectorInput.getAmazonS3PlatformDetails().getValue().orElse(null);
    }
    return null;
  }

  private QLGCSPlatformInput getGCSPlatformInput(QLHelmConnectorInput helmConnectorInput) {
    if (helmConnectorInput.getGcsPlatformDetails().isPresent()) {
      return helmConnectorInput.getGcsPlatformDetails().getValue().orElse(null);
    }
    return null;
  }

  private QLHttpServerPlatformInput getHttpServerPlatformInput(QLHelmConnectorInput helmConnectorInput) {
    if (helmConnectorInput.getHttpServerPlatformDetails().isPresent()) {
      return helmConnectorInput.getHttpServerPlatformDetails().getValue().orElse(null);
    }
    return null;
  }

  private QLOCIPlatformInput getOCIPlatformInput(QLHelmConnectorInput helmConnectorInput) {
    if (helmConnectorInput.getOciPlatformDetails().isPresent()) {
      return helmConnectorInput.getOciPlatformDetails().getValue().orElse(null);
    }
    return null;
  }

  private void checkUserNameExists(QLHttpServerPlatformInput httpServerPlatformInput) {
    RequestField<String> passwordSecretId = httpServerPlatformInput.getPasswordSecretId();
    RequestField<String> userName = httpServerPlatformInput.getUserName();
    Optional<String> userNameValue;

    if (passwordSecretId.isPresent() && passwordSecretId.getValue().isPresent()) {
      if (userName.isPresent()) {
        userNameValue = userName.getValue();
        if (!userNameValue.isPresent() || StringUtils.isBlank(userNameValue.get())) {
          throw new InvalidRequestException("userName should be specified");
        }
      } else {
        throw new InvalidRequestException("userName is not specified");
      }
    }
  }
  private void checkUserNameExists(QLOCIPlatformInput httpServerPlatformInput) {
    RequestField<String> passwordSecretId = httpServerPlatformInput.getPasswordSecretId();
    RequestField<String> userName = httpServerPlatformInput.getUserName();
    Optional<String> userNameValue;

    if (passwordSecretId.isPresent() && passwordSecretId.getValue().isPresent()) {
      if (userName.isPresent()) {
        userNameValue = userName.getValue();
        if (!userNameValue.isPresent() || StringUtils.isBlank(userNameValue.get())) {
          throw new InvalidRequestException("userName should be specified");
        }
      } else {
        throw new InvalidRequestException("userName is not specified");
      }
    }
  }

  private void setUrl(QLHttpServerPlatformInput httpServerPlatformInput, HttpHelmRepoConfig httpHelmRepoConfig) {
    if (httpServerPlatformInput.getURL().isPresent()) {
      String url;
      Optional<String> urlValue = httpServerPlatformInput.getURL().getValue();
      if (urlValue.isPresent() && StringUtils.isNotBlank(urlValue.get())) {
        url = urlValue.get().trim();
      } else {
        throw new InvalidRequestException("URL should be specified");
      }
      httpHelmRepoConfig.setChartRepoUrl(url);
    }
  }

  private void setUrl(QLOCIPlatformInput ociPlatformInput, OciHelmRepoConfig ociHelmRepoConfig) {
    if (ociPlatformInput.getURL().isPresent()) {
      String url;
      Optional<String> urlValue = ociPlatformInput.getURL().getValue();
      if (!urlValue.isPresent() || StringUtils.isBlank(urlValue.get())) {
        throw new InvalidRequestException("URL should be specified");
      }

      url = urlValue.get().trim();
      ociHelmRepoConfig.setChartRepoUrl(url);
    }
  }

  private void setUsernameAndPassword(
      QLHttpServerPlatformInput httpServerPlatformInput, HttpHelmRepoConfig httpHelmRepoConfig) {
    if (httpServerPlatformInput.getPasswordSecretId().isPresent()) {
      httpServerPlatformInput.getPasswordSecretId().getValue().ifPresent(httpHelmRepoConfig::setEncryptedPassword);
    }
    if (httpServerPlatformInput.getUserName().isPresent()) {
      httpServerPlatformInput.getUserName().getValue().ifPresent(
          userName -> httpHelmRepoConfig.setUsername(userName.trim()));
    }
  }

  private void setUsernameAndPassword(QLOCIPlatformInput ociPlatformInput, OciHelmRepoConfig ociHelmRepoConfig) {
    if (ociPlatformInput.getPasswordSecretId().isPresent()) {
      ociPlatformInput.getPasswordSecretId().getValue().ifPresent(ociHelmRepoConfig::setEncryptedPassword);
    }
    if (ociPlatformInput.getUserName().isPresent()) {
      ociPlatformInput.getUserName().getValue().ifPresent(userName -> ociHelmRepoConfig.setUsername(userName.trim()));
    }
  }

  private void setAmazonS3PlatformDetails(
      QLAmazonS3PlatformInput amazonS3PlatformInput, AmazonS3HelmRepoConfig amazonS3HelmRepoConfig, String accountId) {
    amazonS3HelmRepoConfig.setConnectorId(getAWSCloudProviderId(accountId, amazonS3PlatformInput));

    if (!amazonS3PlatformInput.getBucketName().isPresent()) {
      throw new InvalidRequestException("Bucket name is not specified for Amazon S3 hosting platform");
    }
    if (!amazonS3PlatformInput.getRegion().isPresent()) {
      throw new InvalidRequestException("Region is not specified for Amazon S3 hosting platform");
    }

    Optional<String> amazonS3Bucket = amazonS3PlatformInput.getBucketName().getValue();
    Optional<String> region = amazonS3PlatformInput.getRegion().getValue();

    if (amazonS3Bucket.isPresent() && StringUtils.isNotBlank(amazonS3Bucket.get())) {
      amazonS3HelmRepoConfig.setBucketName(amazonS3Bucket.get().trim());
    } else {
      throw new InvalidRequestException("Bucket name should be specified for Amazon S3 hosting platform");
    }
    if (region.isPresent() && StringUtils.isNotBlank(region.get().trim())) {
      amazonS3HelmRepoConfig.setRegion(region.get());
    } else {
      throw new InvalidRequestException("Region should be specified for Amazon S3 hosting platform");
    }
  }

  private void setGCSPlatformDetails(
      QLGCSPlatformInput gcsPlatformInput, GCSHelmRepoConfig gcsHelmRepoConfig, String accountId) {
    gcsHelmRepoConfig.setConnectorId(getGoogleCloudProviderId(accountId, gcsPlatformInput));

    if (!gcsPlatformInput.getBucketName().isPresent()) {
      throw new InvalidRequestException("Bucket name is not specified for GCS hosting platform");
    }
    Optional<String> bucketName = gcsPlatformInput.getBucketName().getValue();

    if (bucketName.isPresent() && StringUtils.isNotBlank(bucketName.get())) {
      gcsHelmRepoConfig.setBucketName(bucketName.get().trim());
    } else {
      throw new InvalidRequestException("Bucket name should be specified for GCS hosting platform");
    }
  }

  private SettingAttribute getSettingAttribute(SettingValue settingValue, String accountId) {
    return SettingAttribute.Builder.aSettingAttribute()
        .withValue(settingValue)
        .withAccountId(accountId)
        .withCategory(SettingAttribute.SettingCategory.SETTING)
        .build();
  }

  private String getAWSCloudProviderId(String accountId, QLAmazonS3PlatformInput amazonS3PlatformInput) {
    if (!amazonS3PlatformInput.getAwsCloudProvider().isPresent()) {
      throw new InvalidRequestException("AWS Cloud provider is not specified for Amazon S3 hosting platform");
    }
    Optional<String> awsCloudProvider = amazonS3PlatformInput.getAwsCloudProvider().getValue();

    if (awsCloudProvider.isPresent() && StringUtils.isNotBlank(awsCloudProvider.get())) {
      SettingAttribute settingAttributeById =
          settingsService.getByAccountAndId(accountId, awsCloudProvider.get().trim());
      if (settingAttributeById == null || settingAttributeById.getValue() == null) {
        throw new InvalidRequestException("AWS Cloud provider does not exist");
      } else {
        return settingAttributeById.getUuid();
      }
    } else {
      throw new InvalidRequestException("AWS Cloud provider should be specified for Amazon S3 hosting platform");
    }
  }

  private String getGoogleCloudProviderId(String accountId, QLGCSPlatformInput gcsPlatformInput) {
    if (!gcsPlatformInput.getGoogleCloudProvider().isPresent()) {
      throw new InvalidRequestException("Google Cloud provider is not specified for GCS hosting platform");
    }
    Optional<String> googleCloudProvider = gcsPlatformInput.getGoogleCloudProvider().getValue();

    if (googleCloudProvider.isPresent() && StringUtils.isNotBlank(googleCloudProvider.get())) {
      SettingAttribute settingAttributeById =
          settingsService.getByAccountAndId(accountId, googleCloudProvider.get().trim());
      if (settingAttributeById == null || settingAttributeById.getValue() == null) {
        throw new InvalidRequestException("Google Cloud provider does not exist");
      } else {
        return settingAttributeById.getUuid();
      }
    } else {
      throw new InvalidRequestException("Google Cloud provider should be specified for GCS hosting platform");
    }
  }
}
