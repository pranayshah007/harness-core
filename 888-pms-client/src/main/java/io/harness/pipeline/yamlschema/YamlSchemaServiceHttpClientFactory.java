/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pipeline.yamlschema;

import com.google.inject.Provider;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;


@OwnedBy(PIPELINE)
public class YamlSchemaServiceHttpClientFactory extends AbstractHttpClientFactory implements Provider<YamlSchemaServiceClient>{

  public YamlSchemaServiceHttpClientFactory(ServiceHttpClientConfig config, String serviceSecret,
            ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId) {
        super(config, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false, ClientMode.PRIVILEGED);
    }

        @Override
        public YamlSchemaServiceClient get() {
           return getRetrofit().create(YamlSchemaServiceClient.class);
    }
}
