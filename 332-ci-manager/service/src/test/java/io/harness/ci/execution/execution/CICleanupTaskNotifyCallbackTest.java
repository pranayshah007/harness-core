/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CICleanupTaskNotifyCallbackTest extends CIExecutionTestBase {
  @Mock private HPersistence persistence;
  @Mock private Query<CIResourceCleanup> mockQuery;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @InjectMocks private CICleanupTaskNotifyCallback ciCleanupTaskNotifyCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackK8Success() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get())).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackK8Failure() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get())).thenCallRealMethod();
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackVMSuccess() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get()))
        .thenReturn(responseSupplier.get("taskID").get());
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(persistence.delete((Query<PersistentEntity>) any())).thenReturn(true);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(1)).delete((Query<PersistentEntity>) any());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testNotifyCallbackVMFailure() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("taskID",
        () -> VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
    when(serializedResponseDataHelper.deserialize(responseSupplier.get("taskID").get()))
        .thenReturn(responseSupplier.get("taskID").get());
    when(persistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    ciCleanupTaskNotifyCallback.notify(responseSupplier);
    verify(persistence, times(0)).delete((Query<PersistentEntity>) any());
  }
}
