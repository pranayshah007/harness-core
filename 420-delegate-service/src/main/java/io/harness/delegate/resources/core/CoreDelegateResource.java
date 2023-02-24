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
import io.harness.delegate.core.AcquireTasksResponse;
import io.harness.delegate.core.ExecutionEnvironment;
import io.harness.delegate.core.ExecutionMode;
import io.harness.delegate.core.ExecutionPriority;
import io.harness.delegate.core.PluginDescriptor;
import io.harness.delegate.core.PluginInput;
import io.harness.delegate.core.PluginSecret;
import io.harness.delegate.core.PluginSource;
import io.harness.delegate.core.PluginType;
import io.harness.delegate.core.ResourceRequirements;
import io.harness.delegate.core.SecretConfig;
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
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
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

      // Wrap DelegateTaskPackage with AcquireTaskResponse for Kryo tasks
      final var taskDataBytes = kryoSerializer.asDeflatedBytes(delegateTaskPackage);
      final List<PluginSecret> protoSecrets = createProtoSecrets(delegateTaskPackage);

      final var pluginDesc =
          PluginDescriptor.newBuilder()
              .setId(taskId)
              .setMode(ExecutionMode.MODE_ONCE)
              .setPriority(delegateTaskPackage.getData().isAsync() ? ExecutionPriority.PRIORITY_DEFAULT
                                                                   : ExecutionPriority.PRIORITY_HIGH)
              .setInput(PluginInput.newBuilder().setBinaryData(ByteString.copyFrom(taskDataBytes)).build())
              .addAllInputSecrets(protoSecrets)
              .setRuntime(
                  ExecutionEnvironment.newBuilder()
                      .setType(PluginType.newBuilder().setType(delegateTaskPackage.getData().getTaskType()).build())
                      .setSource(PluginSource.SOURCE_IMAGE)
                      .setUsing("us.gcr.io/gcr-play/delegate-plugin:k8s")
                      .setResource(ResourceRequirements.newBuilder()
                                       .setMemory("128Mi")
                                       .setCpu("0.1")
                                       .setTimeout(Duration.newBuilder().setSeconds(timeout).build())
                                       .build())
                      .build())
              .build();
      final var response = AcquireTasksResponse.newBuilder().addTasks(pluginDesc).build();

      return Response.ok(response).build();
    } catch (final Exception e) {
      log.error("Exception serializing task {} data ", taskId, e);
      return Response.serverError().build();
    }
  }

  private List<PluginSecret> createProtoSecrets(final DelegateTaskPackage delegateTaskPackage) {
    final Map<EncryptionConfig, List<EncryptedRecord>> kryoSecrets =
        delegateTaskPackage.getSecretDetails().values().stream().collect(Collectors.groupingBy(secret
            -> delegateTaskPackage.getEncryptionConfigs().get(secret.getConfigUuid()),
            Collectors.mapping(SecretDetail::getEncryptedRecord, Collectors.toList())));

    return kryoSecrets.entrySet()
        .stream()
        .map(entry -> createProtoSecret(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  private PluginSecret createProtoSecret(final EncryptionConfig config, final List<EncryptedRecord> secrets) {
    final var configBytes = kryoSerializer.asDeflatedBytes(config);
    final var secretsBytes = kryoSerializer.asDeflatedBytes(secrets);

    return PluginSecret.newBuilder()
        .setConfig(SecretConfig.newBuilder().setBinaryData(ByteString.copyFrom(configBytes)).build())
        .setSecrets(PluginInput.newBuilder().setBinaryData(ByteString.copyFrom(secretsBytes)).build())
        .setRuntime(
            ExecutionEnvironment.newBuilder()
                .setType(
                    PluginType.newBuilder().setType("SECRET").build()) // Fixme: Secret type doesn't exist right now
                .setSource(PluginSource.SOURCE_IMAGE)
                .setUsing("us.gcr.io/gcr-play/secret-provider:secrets")
                .setResource(ResourceRequirements.newBuilder()
                                 .setMemory("128Mi")
                                 .setCpu("0.1")
                                 .setTimeout(Duration.newBuilder().setSeconds(600).build())
                                 .build())
                .build())
        .build();
  }
}
