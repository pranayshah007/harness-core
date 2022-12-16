/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iacm.beans.entities.IACMServiceConfig;
import io.harness.network.Http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@OwnedBy(HarnessTeam.IACM)
public class IACMServiceClientFactory implements Provider<IACMServiceClient> {
  private IACMServiceConfig iacmConfig;

  @Inject
  public IACMServiceClientFactory(IACMServiceConfig iacmConfig) {
    this.iacmConfig = iacmConfig;
  }

  @Override
  public IACMServiceClient get() {
    Gson gson = new GsonBuilder().setLenient().create();
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(iacmConfig.getBaseUrl())
                            .client(Http.getUnsafeOkHttpClient(iacmConfig.getBaseUrl()))
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
    return retrofit.create(IACMServiceClient.class);
  }
}
