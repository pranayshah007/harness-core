/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.K8ActivityDataCollectionInfo;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.rest.RestResponse;
import io.harness.verificationclient.CVNextGenServiceClient;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;

public abstract class ChangeIntelHandlerTestBase extends DelegateTestBase {
  @Mock protected CVNGRequestExecutor cvngRequestExecutor;
  @Mock protected CVNextGenServiceClient cvNextGenServiceClient;
  protected String accountId;
  protected K8ActivityDataCollectionInfo dataCollectionInfo;

  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    accountId = generateUuid();
    dataCollectionInfo = K8ActivityDataCollectionInfo.builder()
                             .changeSourceIdentifier("changesourceId")
                             .envIdentifier("envId")
                             .serviceIdentifier("serviceId")
                             .projectIdentifier("projectId")
                             .orgIdentifier("orgId")
                             .build();

    Call<RestResponse<Boolean>> call = Mockito.mock(Call.class);
    when(cvNextGenServiceClient.saveChangeEvent(anyString(), any(ChangeEventDTO.class))).thenReturn(call);
    when(cvngRequestExecutor.executeWithRetry(any(Call.class))).thenReturn(new RestResponse<>(true));
  }

  protected ChangeEventDTO verifyAndValidate() {
    ArgumentCaptor<ChangeEventDTO> argumentCaptor = ArgumentCaptor.forClass(ChangeEventDTO.class);
    verify(cvNextGenServiceClient).saveChangeEvent(anyString(), argumentCaptor.capture());
    ChangeEventDTO eventDTO = argumentCaptor.getValue();
    assertThat(eventDTO).isNotNull();
    assertThat(eventDTO.getAccountId()).isEqualTo(accountId);
    assertThat(eventDTO.getChangeSourceIdentifier()).isEqualTo(dataCollectionInfo.getChangeSourceIdentifier());
    assertThat(eventDTO.getMonitoredServiceIdentifier()).isEqualTo(dataCollectionInfo.getMonitoredServiceIdentifier());
    assertThat(eventDTO.getEnvIdentifier()).isEqualTo(dataCollectionInfo.getEnvIdentifier());
    assertThat(eventDTO.getServiceIdentifier()).isEqualTo(dataCollectionInfo.getServiceIdentifier());
    assertThat(eventDTO.getOrgIdentifier()).isEqualTo(dataCollectionInfo.getOrgIdentifier());
    assertThat(eventDTO.getType().name()).isEqualTo(ChangeSourceType.KUBERNETES.name());
    return eventDTO;
  }
}
