/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.RISHABH;

import static java.util.Objects.isNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorInfoOutcomeDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.outcome.ArtifactoryAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.outcome.ArtifactoryConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsSdkClientBackoffStrategyDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.awsconnector.outcome.AwsSdkClientBackoffStrategyOutcomeDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.outcome.AzureArtifactsAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.azureartifacts.outcome.AzureArtifactsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.azureartifacts.outcome.AzureArtifactsCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSystemAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureAuthCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureAuthOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureClientKeyCertOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureClientSecretKeyOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureCredentialSpecOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureInheritFromDelegateDetailsOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthSAOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthUAOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureManualDetailsOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureSystemAssignedMSIAuthOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureUserAssignedMSIAuthOutcomeDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.outcome.DockerAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.docker.outcome.DockerConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.outcome.GcpConnectorCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.gcpconnector.outcome.GcpConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.outcome.HttpHelmAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.helm.outcome.HttpHelmConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.helm.outcome.OciHelmAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.helm.outcome.OciHelmConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.outcome.JenkinsAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.jenkins.outcome.JenkinsConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesDelegateDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesAuthOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterConfigOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesClusterDetailsOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesCredentialOutcomeDTO;
import io.harness.delegate.beans.connector.k8Connector.outcome.KubernetesDelegateDetailsOutcomeDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.outcome.NexusAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.nexusconnector.outcome.NexusConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.beans.connector.pdcconnector.outcome.HostOutcomeDTO;
import io.harness.delegate.beans.connector.pdcconnector.outcome.PhysicalDataCenterConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.outcome.AwsCodeCommitAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.outcome.AwsCodeCommitConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.outcome.AwsCodeCommitCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.outcome.AwsCodeCommitHttpsCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoHttpCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.outcome.AzureRepoSshCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketHttpCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketSshCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitConfigOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitHTTPAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitSSHAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubHttpCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.github.outcome.GithubSshCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabHttpCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabSshCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.outcome.TasConnectorOutcomeDTO;
import io.harness.delegate.beans.connector.tasconnector.outcome.TasCredentialOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ClassUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ConnectorOutcomeTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFieldNamesForConnectorOutcome() {
    List<Class<?>> classesToTest = Arrays.asList(ConnectorInfoOutcomeDTO.class, AzureMSIAuthSAOutcomeDTO.class,
        AzureMSIAuthUAOutcomeDTO.class, GcpDelegateDetailsDTO.class);
    for (Class<?> classToTest : classesToTest) {
      getExpressionsInObject(classToTest, "");
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCompareNumberOfFields() {
    Map<Class<?>, Class<?>> classToOutcomeClass = new HashMap<>();
    classToOutcomeClass.put(ConnectorConfigDTO.class, ConnectorConfigOutcomeDTO.class);
    classToOutcomeClass.put(ConnectorInfoDTO.class, ConnectorInfoOutcomeDTO.class);
    classToOutcomeClass.put(AwsConnectorDTO.class, AwsConnectorOutcomeDTO.class);
    classToOutcomeClass.put(AwsCredentialDTO.class, AwsCredentialOutcomeDTO.class);
    classToOutcomeClass.put(AwsSdkClientBackoffStrategyDTO.class, AwsSdkClientBackoffStrategyOutcomeDTO.class);
    classToOutcomeClass.put(AzureAuthCredentialDTO.class, AzureAuthCredentialOutcomeDTO.class);
    classToOutcomeClass.put(AzureAuthDTO.class, AzureAuthOutcomeDTO.class);
    classToOutcomeClass.put(AzureClientKeyCertDTO.class, AzureClientKeyCertOutcomeDTO.class);
    classToOutcomeClass.put(AzureClientSecretKeyDTO.class, AzureClientSecretKeyOutcomeDTO.class);
    classToOutcomeClass.put(AzureConnectorDTO.class, AzureConnectorOutcomeDTO.class);
    classToOutcomeClass.put(AzureCredentialDTO.class, AzureCredentialOutcomeDTO.class);
    classToOutcomeClass.put(AzureCredentialSpecDTO.class, AzureCredentialSpecOutcomeDTO.class);
    classToOutcomeClass.put(AzureInheritFromDelegateDetailsDTO.class, AzureInheritFromDelegateDetailsOutcomeDTO.class);
    classToOutcomeClass.put(AzureMSIAuthDTO.class, AzureMSIAuthOutcomeDTO.class);
    classToOutcomeClass.put(AzureMSIAuthUADTO.class, AzureMSIAuthUAOutcomeDTO.class);
    classToOutcomeClass.put(AzureMSIAuthSADTO.class, AzureMSIAuthSAOutcomeDTO.class);
    classToOutcomeClass.put(AzureManualDetailsDTO.class, AzureManualDetailsOutcomeDTO.class);
    classToOutcomeClass.put(AzureSystemAssignedMSIAuthDTO.class, AzureSystemAssignedMSIAuthOutcomeDTO.class);
    classToOutcomeClass.put(AzureUserAssignedMSIAuthDTO.class, AzureUserAssignedMSIAuthOutcomeDTO.class);
    classToOutcomeClass.put(GcpConnectorCredentialDTO.class, GcpConnectorCredentialOutcomeDTO.class);
    classToOutcomeClass.put(GcpConnectorDTO.class, GcpConnectorOutcomeDTO.class);
    classToOutcomeClass.put(KubernetesAuthDTO.class, KubernetesAuthOutcomeDTO.class);
    classToOutcomeClass.put(KubernetesClusterConfigDTO.class, KubernetesClusterConfigOutcomeDTO.class);
    classToOutcomeClass.put(KubernetesClusterDetailsDTO.class, KubernetesClusterDetailsOutcomeDTO.class);
    classToOutcomeClass.put(KubernetesCredentialDTO.class, KubernetesCredentialOutcomeDTO.class);
    classToOutcomeClass.put(KubernetesDelegateDetailsDTO.class, KubernetesDelegateDetailsOutcomeDTO.class);
    classToOutcomeClass.put(HostDTO.class, HostOutcomeDTO.class);
    classToOutcomeClass.put(PhysicalDataCenterConnectorDTO.class, PhysicalDataCenterConnectorOutcomeDTO.class);
    classToOutcomeClass.put(GitAuthenticationDTO.class, GitAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(GitConfigDTO.class, GitConfigOutcomeDTO.class);
    classToOutcomeClass.put(GitHTTPAuthenticationDTO.class, GitHTTPAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(GitSSHAuthenticationDTO.class, GitSSHAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(GithubAuthenticationDTO.class, GithubAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(GithubHttpCredentialsDTO.class, GithubHttpCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(GithubSshCredentialsDTO.class, GithubSshCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(GitlabAuthenticationDTO.class, GitlabAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(GitlabHttpCredentialsDTO.class, GitlabHttpCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(GitlabSshCredentialsDTO.class, GitlabSshCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(TasConnectorDTO.class, TasConnectorOutcomeDTO.class);
    classToOutcomeClass.put(TasCredentialDTO.class, TasCredentialOutcomeDTO.class);
    classToOutcomeClass.put(ArtifactoryConnectorDTO.class, ArtifactoryConnectorOutcomeDTO.class);
    classToOutcomeClass.put(ArtifactoryAuthenticationDTO.class, ArtifactoryAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(DockerConnectorDTO.class, DockerConnectorOutcomeDTO.class);
    classToOutcomeClass.put(DockerAuthenticationDTO.class, DockerAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(AzureArtifactsConnectorDTO.class, AzureArtifactsConnectorOutcomeDTO.class);
    classToOutcomeClass.put(AzureArtifactsAuthenticationDTO.class, AzureArtifactsAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(AzureArtifactsCredentialsDTO.class, AzureArtifactsCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(HttpHelmConnectorDTO.class, HttpHelmConnectorOutcomeDTO.class);
    classToOutcomeClass.put(HttpHelmAuthenticationDTO.class, HttpHelmAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(OciHelmConnectorDTO.class, OciHelmConnectorOutcomeDTO.class);
    classToOutcomeClass.put(OciHelmAuthenticationDTO.class, OciHelmAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(JenkinsAuthenticationDTO.class, JenkinsAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(JenkinsConnectorDTO.class, JenkinsConnectorOutcomeDTO.class);
    classToOutcomeClass.put(NexusConnectorDTO.class, NexusConnectorOutcomeDTO.class);
    classToOutcomeClass.put(NexusAuthenticationOutcomeDTO.class, NexusAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(AwsCodeCommitHttpsCredentialsDTO.class, AwsCodeCommitHttpsCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(AwsCodeCommitAuthenticationDTO.class, AwsCodeCommitAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(AwsCodeCommitConnectorDTO.class, AwsCodeCommitConnectorOutcomeDTO.class);
    classToOutcomeClass.put(AwsCodeCommitCredentialsDTO.class, AwsCodeCommitCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(AzureRepoAuthenticationDTO.class, AzureRepoAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(AzureRepoConnectorDTO.class, AzureRepoConnectorOutcomeDTO.class);
    classToOutcomeClass.put(AzureRepoCredentialsDTO.class, AzureRepoCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(AzureRepoHttpCredentialsDTO.class, AzureRepoHttpCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(AzureRepoSshCredentialsDTO.class, AzureRepoSshCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(BitbucketAuthenticationDTO.class, BitbucketAuthenticationOutcomeDTO.class);
    classToOutcomeClass.put(BitbucketConnectorDTO.class, BitbucketConnectorOutcomeDTO.class);
    classToOutcomeClass.put(BitbucketCredentialsDTO.class, BitbucketCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(BitbucketHttpCredentialsDTO.class, BitbucketHttpCredentialsOutcomeDTO.class);
    classToOutcomeClass.put(BitbucketSshCredentialsDTO.class, BitbucketSshCredentialsOutcomeDTO.class);

    for (Map.Entry<Class<?>, Class<?>> entry : classToOutcomeClass.entrySet()) {
      compareNumOfFields(entry.getKey(), entry.getValue());
    }
  }

  public boolean checkIfClassIsCollection(Field declaredField) {
    return Collection.class.isAssignableFrom(declaredField.getType());
  }

  public JsonSubTypes getJsonSubTypes(Field field) {
    JsonSubTypes annotation = field.getAnnotation(JsonSubTypes.class);
    if (annotation == null || isEmpty(annotation.value())) {
      annotation = field.getType().getAnnotation(JsonSubTypes.class);
    }
    if (checkIfClassIsCollection(field)) {
      ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
      Class<?> collectionTypeClass = (Class<?>) collectionType.getActualTypeArguments()[0];
      annotation = collectionTypeClass.getAnnotation(JsonSubTypes.class);
    }
    if (annotation == null || isEmpty(annotation.value())) {
      return null;
    }
    return annotation;
  }

  public void compareNumOfFields(Class<?> mainClass, Class<?> outcomeClass) {
    List<Field> mainClassFields = ReflectionUtils.getAllDeclaredAndInheritedFields(mainClass);
    List<Field> outcomeClassFields = ReflectionUtils.getAllDeclaredAndInheritedFields(outcomeClass);
    assertThat(mainClassFields)
        .hasSize(outcomeClassFields.size())
        .withFailMessage(
            "Number of fields doesn't match for class: %s and %s", mainClass.getName(), outcomeClass.getName());
  }

  // Traverses the object checking values inside the object and return their expressions
  public List<String> getExpressionsInObject(Class<?> c, String prefix) {
    List<Field> fields = ReflectionUtils.getAllDeclaredAndInheritedFields(c);
    List<String> resultantFieldExpressions = new LinkedList<>();
    for (Field field : fields) {
      field.setAccessible(true);
      String fieldName = getFieldName(field, c);
      JsonSubTypes annotations = getJsonSubTypes(field);
      String mergedFqn = getMergedFqn(prefix, fieldName);
      if (!isNull(annotations)) {
        for (JsonSubTypes.Type annotation : annotations.value()) {
          if (!isLeafVariable(null, annotation.value(), false)) {
            getExpressionsInObject(annotation.value(), mergedFqn);
          }
        }
      }
      try {
        if (!isLeafVariable(field, field.getType(), true)) {
          addExpressionInCustomObject(field, field.getType(), mergedFqn);
        }
      } catch (Exception e) {
        throw new InvalidRequestException("Unable to get field property in variables expression: " + e.getMessage());
      }
    }
    return resultantFieldExpressions;
  }

  private String getMergedFqn(String prefix, String fieldPath) {
    if (EmptyPredicate.isEmpty(fieldPath)) {
      return prefix;
    }
    if (EmptyPredicate.isEmpty(prefix)) {
      return fieldPath;
    }
    return prefix + "." + fieldPath;
  }

  private void addExpressionInCustomObject(Field field, Class<?> fieldValue, String mergedFqn) {
    if (Map.class.isAssignableFrom(fieldValue)) {
      Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
      for (Type type : types) {
        if (!isLeafVariable(null, (Class<?>) type, false)) {
          getExpressionsInObject((Class<?>) type, mergedFqn);
        }
      }
    } else {
      getExpressionsInObject(fieldValue, mergedFqn);
    }
  }

  private String getFieldName(Field field, Class<?> c) {
    if (field.isAnnotationPresent(JsonProperty.class)) {
      JsonProperty jsonPropertyAnnotation = field.getAnnotation(JsonProperty.class);
      if (!field.getName().equals(jsonPropertyAnnotation.value())) {
        throw new InvalidYamlException(
            "Field name and JsonProperty name doesn't match for field " + field.getType() + " in " + c.getName());
      }
    }
    return field.getName();
  }

  private boolean isPrimitiveDataType(Class<?> cls) {
    return String.class.isAssignableFrom(cls) || ClassUtils.isPrimitiveOrWrapper(cls) || cls.isEnum();
  }

  private boolean isLeafVariable(Field field, Class<?> fieldValue, Boolean checkField) {
    if (fieldValue == null || isPrimitiveDataType(fieldValue)) {
      return true;
    }
    return checkField && isCustomObjectAsLeafField(field, fieldValue);
  }

  private boolean isCustomObjectAsLeafField(Field field, Class<?> fieldValue) {
    if (field != null && field.isAnnotationPresent(VariableExpression.class)) {
      return field.getAnnotation(VariableExpression.class).skipInnerObjectTraversal();
    }
    // if value is null
    return fieldValue == null;
  }
}
