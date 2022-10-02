/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.cvng.beans.HostRecordDTO;
import io.harness.rest.RestResponse;
import io.harness.verificationclient.CVNextGenServiceClient;

import com.google.common.collect.Lists;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class HostRecordDataStoreService {
  @Inject private CVNextGenServiceClient cvNextGenServiceClient;

  public void save(String accountId, String verificationTaskId, Instant startTime, Instant endTime, Set<String> hosts) {
    // TODO: find a way to implement retry and time limiting. The timeout should be implemented using retrofit config
    //  and not using TimeLimiter class as it has potential for memory leak. Also the exceptions needs to propagate from
    //  cv-nextgen to delegate.
    try {
      Response<RestResponse<Void>> response = cvNextGenServiceClient
                                                  .saveHostRecords(accountId,
                                                      Lists.newArrayList(HostRecordDTO.builder()
                                                                             .accountId(accountId)
                                                                             .verificationTaskId(verificationTaskId)
                                                                             .startTime(startTime)
                                                                             .endTime(endTime)
                                                                             .hosts(hosts)
                                                                             .build()))
                                                  .execute();
      if (!response.isSuccessful()) {
        throw new IllegalStateException("Request not successful");
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
