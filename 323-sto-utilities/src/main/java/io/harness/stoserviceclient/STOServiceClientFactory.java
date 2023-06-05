/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stoserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.network.Http;
import io.harness.sto.beans.entities.STOServiceConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@OwnedBy(HarnessTeam.STO)
public class STOServiceClientFactory implements Provider<STOServiceClient> {
  private STOServiceConfig stoConfig;

  @Inject
  public STOServiceClientFactory(STOServiceConfig stoConfig) {
    this.stoConfig = stoConfig;
  }

  @Override
  public STOServiceClient get() {
    Gson gson = new GsonBuilder().setLenient().create();
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(stoConfig.getInternalUrl())
                            .client(Http.getUnsafeOkHttpClient(stoConfig.getInternalUrl()))
                            .addConverterFactory(GsonConverterFactory.create(gson))
                            .build();
    return retrofit.create(STOServiceClient.class);
  }
}
