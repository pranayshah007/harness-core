/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.beans.KeyValuePair;
import io.harness.context.GlobalContext;
import io.harness.context.GlobalContextData;
import io.harness.context.MdcGlobalContextData;
import io.harness.encryption.Scope;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.AdfsAuthException;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.AuthenticationException;
import io.harness.exception.AuthorizationException;
import io.harness.exception.AzureAKSException;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.AzureConfigException;
import io.harness.exception.AzureContainerRegistryException;
import io.harness.exception.AzureServerException;
import io.harness.exception.ConnectException;
import io.harness.exception.ContextException;
import io.harness.exception.DataProcessingException;
import io.harness.exception.DelegateErrorHandlerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.EngineExpressionEvaluationException;
import io.harness.exception.EngineFunctorException;
import io.harness.exception.ExceptionHandlerNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.FailureType;
import io.harness.exception.FunctorException;
import io.harness.exception.GcpServerException;
import io.harness.exception.GeneralException;
import io.harness.exception.GitOperationException;
import io.harness.exception.HintException;
import io.harness.exception.HttpResponseException;
import io.harness.exception.IllegalArgumentException;
import io.harness.exception.ImageNotFoundException;
import io.harness.exception.InterruptedRuntimeException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidCredentialsException;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidTagException;
import io.harness.exception.InvalidThirdPartyCredentialsException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.JiraClientException;
import io.harness.exception.KryoHandlerNotFoundException;
import io.harness.exception.KubernetesApiTaskException;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.KubernetesYamlException;
import io.harness.exception.NexusRegistryException;
import io.harness.exception.NexusServerException;
import io.harness.exception.SecretNotFoundException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.ServiceNowOIDCException;
import io.harness.exception.ShellExecutionException;
import io.harness.exception.TerraformCloudException;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.TerragruntCommandExecutionException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.AzureARMTaskException;
import io.harness.exception.ngexception.AzureAppServiceTaskException;
import io.harness.exception.ngexception.AzureBPTaskException;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorDTO;
import io.harness.exception.ngexception.beans.templateservice.TemplateInputsErrorMetadataDTO;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.runtime.serverless.ServerlessAwsLambdaRuntimeException;
import io.harness.exception.runtime.serverless.ServerlessCommandExecutionException;
import io.harness.logging.LogLevel;
import io.harness.rest.RestResponse;
import io.harness.security.PrincipalContextData;
import io.harness.security.SimpleEncryption;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;
import io.harness.serializer.KryoRegistrar;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.FailureResponseData;

import software.wings.beans.NameValuePair;
import software.wings.beans.NameValuePairWithDefault;
import software.wings.beans.appmanifest.StoreType;

import com.esotericsoftware.kryo.Kryo;
import java.net.SocketException;
import java.time.ZonedDateTime;

@OwnedBy(HarnessTeam.PL)
public class CommonsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(FileData.class, 1201);
    kryo.register(GlobalContext.class, 1202);
    kryo.register(GlobalContextData.class, 1203);
    kryo.register(SocketException.class, 1204);
    kryo.register(FailureType.class, 1205);
    kryo.register(MdcGlobalContextData.class, 1206);

    kryo.register(ErrorCode.class, 5233);
    kryo.register(Level.class, 5590);
    kryo.register(ResponseMessage.class, 5316);
    kryo.register(RestResponse.class, 5224);

    kryo.register(ExplanationException.class, 5324);
    kryo.register(FunctorException.class, 5589);
    kryo.register(HintException.class, 5325);
    kryo.register(InvalidArgumentsException.class, 5326);
    kryo.register(InvalidRequestException.class, 5327);
    kryo.register(UnauthorizedException.class, 5329);
    kryo.register(UnexpectedException.class, 5330);
    kryo.register(WingsException.ReportTarget.class, 5348);
    kryo.register(WingsException.class, 5174);

    kryo.register(VerificationOperationException.class, 3001);
    kryo.register(ServiceNowException.class, 3002);
    kryo.register(Scope.class, 3004);
    kryo.register(GeneralException.class, 3005);
    kryo.register(BinaryResponseData.class, 3006);
    kryo.register(FailureResponseData.class, 3007);
    kryo.register(KeyValuePair.class, 3008);
    kryo.register(ZonedDateTime.class, 71123);
    kryo.register(StoreType.class, 5540);

    // Promoted Classes
    kryo.register(ArtifactoryServerException.class, 7214);
    kryo.register(ArtifactoryRegistryException.class, 7217);
    kryo.register(NexusServerException.class, 7218);
    kryo.register(NexusRegistryException.class, 7246);
    kryo.register(ArtifactServerException.class, 7244);
    kryo.register(InvalidArtifactServerException.class, 7250);
    kryo.register(ShellExecutionException.class, 7473);
    kryo.register(LogLevel.class, 71103);

    kryo.register(java.lang.StackTraceElement[].class, 31010);
    kryo.register(java.lang.StackTraceElement.class, 31011);
    kryo.register(DelegateErrorHandlerException.class, 31012);
    kryo.register(KryoHandlerNotFoundException.class, 31013);
    kryo.register(ExceptionHandlerNotFoundException.class, 31014);
    kryo.register(ImageNotFoundException.class, 31015);
    kryo.register(HttpResponseException.class, 31016);
    kryo.register(GcpServerException.class, 31017);
    kryo.register(InvalidCredentialsException.class, 31018);
    kryo.register(ContextException.class, 31019);
    kryo.register(InvalidTagException.class, 31020);
    kryo.register(SecretNotFoundException.class, 31021);
    kryo.register(DelegateNotAvailableException.class, 31022);
    kryo.register(IllegalArgumentException.class, 31023);
    kryo.register(InvalidThirdPartyCredentialsException.class, 31024);
    kryo.register(ConnectException.class, 31025);

    kryo.register(PrincipalContextData.class, 980001);
    kryo.register(UserPrincipal.class, 980002);
    kryo.register(SourcePrincipalContextData.class, 980003);
    kryo.register(Principal.class, 980004);
    kryo.register(PrincipalType.class, 980005);
    kryo.register(EngineExpressionEvaluationException.class, 980006);
    kryo.register(EngineFunctorException.class, 980007);
    kryo.register(UnresolvedExpressionsException.class, 980008);
    kryo.register(JiraClientException.class, 980009);
    kryo.register(InvalidYamlException.class, 980010);
    kryo.register(AuthenticationException.class, 980011);
    kryo.register(AuthorizationException.class, 980012);
    kryo.register(KubernetesApiTaskException.class, 980014);
    kryo.register(KubernetesTaskException.class, 980015);
    kryo.register(KubernetesYamlException.class, 980016);
    kryo.register(GitOperationException.class, 980017);
    kryo.register(TerraformCommandExecutionException.class, 980018);
    kryo.register(SimpleEncryption.class, 980019);
    kryo.register(KubernetesCliTaskRuntimeException.class, 980020);

    kryo.register(AzureServerException.class, 980021);
    kryo.register(AzureAuthenticationException.class, 980022);
    kryo.register(AzureConfigException.class, 980023);
    kryo.register(AzureContainerRegistryException.class, 980024);
    kryo.register(DataProcessingException.class, 980025);
    kryo.register(NameValuePair.class, 5226);
    kryo.register(NameValuePairWithDefault.class, 5232);
    kryo.register(ServerlessAwsLambdaRuntimeException.class, 980026);
    kryo.register(ServerlessCommandExecutionException.class, 980027);
    kryo.register(AzureAKSException.class, 980028);
    kryo.register(SshCommandExecutionException.class, 980029);
    kryo.register(InterruptedRuntimeException.class, 980030);
    kryo.register(InvalidIdentifierRefException.class, 980031);
    kryo.register(AzureAppServiceTaskException.class, 980032);
    kryo.register(TemplateInputsErrorDTO.class, 980033);
    kryo.register(TemplateInputsErrorMetadataDTO.class, 980034);
    kryo.register(AzureARMTaskException.class, 980035);
    kryo.register(AzureBPTaskException.class, 980037);
    kryo.register(AdfsAuthException.class, 10000120);
    kryo.register(TerragruntCommandExecutionException.class, 10000262);
    kryo.register(TerraformCloudException.class, 10000305);
    kryo.register(ServiceNowOIDCException.class, 10000122);
  }
}
