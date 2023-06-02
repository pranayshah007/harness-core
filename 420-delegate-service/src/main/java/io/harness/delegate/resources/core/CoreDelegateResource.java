/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources.core;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.core.beans.EmptyDirVolume;
import io.harness.delegate.core.beans.ExecutionMode;
import io.harness.delegate.core.beans.ExecutionPriority;
import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.core.beans.K8SInfra;
import io.harness.delegate.core.beans.K8SStep;
import io.harness.delegate.core.beans.PluginSource;
import io.harness.delegate.core.beans.Resource;
import io.harness.delegate.core.beans.ResourceRequirements;
import io.harness.delegate.core.beans.Secret;
import io.harness.delegate.core.beans.SecretConfig;
import io.harness.delegate.core.beans.StepRuntime;
import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;

import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/agent")
@Path("/agent")
@Consumes(MediaType.APPLICATION_JSON)
@Scope(DELEGATE)
@Slf4j
@OwnedBy(DEL)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateResource {
  private final DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Named("referenceFalseKryoSerializer") private final KryoSerializer kryoSerializer;

  @DelegateAuth
  @GET
  @Path("{delegateId}/tasks/{taskId}/acquire")
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response acquireTask(@PathParam("delegateId") final String delegateId,
      @PathParam("taskId") final String taskId, @QueryParam("accountId") @NotEmpty final String accountId,
      @QueryParam("delegateInstanceId") final String delegateInstanceId) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      final var delegateTaskPackage =
          delegateTaskServiceClassic.acquireDelegateTask(accountId, delegateId, taskId, delegateInstanceId);
      final long timeout = delegateTaskPackage.getData().getTimeout();

      final var logPrefix = generateLogBaseKey(delegateTaskPackage.getLogStreamingAbstractions());

      // Wrap DelegateTaskPackage with AcquireTaskResponse for Kryo tasks
      final var taskDataBytes = kryoSerializer.asBytes(delegateTaskPackage);
      final List<Secret> protoSecrets = createProtoSecrets(delegateTaskPackage);

      final var resources = ResourceRequirements.newBuilder()
                                .setMemory("128Mi")
                                .setCpu("0.1")
                                .setTimeout(Duration.newBuilder().setSeconds(timeout).build())
                                .build();
      final var k8sStep =
          K8SStep.newBuilder()
              .setId(taskId)
              .setMode(ExecutionMode.MODE_ONCE)
              .setPriority(delegateTaskPackage.getData().isAsync() ? ExecutionPriority.PRIORITY_DEFAULT
                                                                   : ExecutionPriority.PRIORITY_HIGH)
              //              .setInput(InputData.newBuilder().setBinaryData(ByteString.copyFrom(taskDataBytes)).build())
              .addAllInputSecrets(protoSecrets)
              .setRuntime(StepRuntime.newBuilder()
                              .setType(delegateTaskPackage.getData().getTaskType())
                              .setSource(PluginSource.SOURCE_IMAGE)
                              .setUses(getImage(delegateTaskPackage.getData().getTaskType()))
                              .setResource(resources)
                              .build())
              .setLoggingToken(delegateTaskPackage.getLogStreamingToken())
              .build();
      final var emptyDir = EmptyDirVolume.newBuilder().setName("marko-dir").setPath("/harness/marko").build();
      final var k8SInfra = K8SInfra
                               .newBuilder()
                               //                               .addAllInfraSecrets(protoSecrets) // Not supported now
                               .addSteps(k8sStep)
                               .setResource(resources)
                               .setWorkingDir("pera")
                               .addResources(Resource.newBuilder().setSpec(Any.pack(emptyDir)).build())
                               .setLogPrefix(logPrefix)
                               .build();

      final TaskPayload task =
          TaskPayload.newBuilder()
              .setInfraData(InputData.newBuilder().setProtoData(Any.pack(k8SInfra)).build()) // Infra input
              .setTaskData(
                  InputData.newBuilder().setBinaryData(ByteString.copyFrom(taskDataBytes)).build()) // Task input
              .build();

      final var response = AcquireTasksResponse.newBuilder().setId(taskId).addTasks(task).build();

      return Response.ok(response).build();
    } catch (final Exception e) {
      log.error("Exception serializing task {} data ", taskId, e);
      return Response.serverError().build();
    }
  }

  private List<Secret> createProtoSecrets(final DelegateTaskPackage delegateTaskPackage) {
    final Map<EncryptionConfig, List<EncryptedRecord>> kryoSecrets =
        delegateTaskPackage.getSecretDetails().values().stream().collect(Collectors.groupingBy(secret
            -> delegateTaskPackage.getEncryptionConfigs().get(secret.getConfigUuid()),
            Collectors.mapping(SecretDetail::getEncryptedRecord, Collectors.toList())));

    return kryoSecrets.entrySet()
        .stream()
        .map(entry -> createProtoSecret(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  private Secret createProtoSecret(final EncryptionConfig config, final List<EncryptedRecord> secrets) {
    final var configBytes = kryoSerializer.asDeflatedBytes(config);
    final var secretsBytes = kryoSerializer.asDeflatedBytes(secrets);

    return Secret.newBuilder()
        .setConfig(SecretConfig.newBuilder().setBinaryData(ByteString.copyFrom(configBytes)).build())
        .setSecrets(InputData.newBuilder().setBinaryData(ByteString.copyFrom(secretsBytes)).build())
        .build();
  }

  private String getImage(final String taskType) {
    switch (taskType) {
      case "K8S_COMMAND_TASK_NG":
        return "us.gcr.io/gcr-play/delegate-plugin:k8s";
      case "SHELL_SCRIPT_TASK_NG":
        return "us.gcr.io/gcr-play/delegate-plugin:shell";
      default:
        throw new UnsupportedOperationException("Unsupported task type " + taskType);
    }
  }

  private String generateLogBaseKey(LinkedHashMap<String, String> logStreamingAbstractions) {
    // Generate base log key that will be used for writing logs to log streaming service
    StringBuilder logBaseKey = new StringBuilder();
    for (Map.Entry<String, String> entry : logStreamingAbstractions.entrySet()) {
      if (logBaseKey.length() != 0) {
        logBaseKey.append('/');
      }
      logBaseKey.append(entry.getKey()).append(':').append(entry.getValue());
    }
    return logBaseKey.toString();
  }
}
