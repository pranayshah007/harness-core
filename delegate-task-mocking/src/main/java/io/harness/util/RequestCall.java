package io.harness.util;


import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.serializer.KryoSerializer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;


@Slf4j
public class RequestCall {
    private KryoSerializer kryoSerializer;
    public String requestNew(DelegateTaskResponse delegateTaskResponse, String delegateTaskId, String delegateId, String accountId, String strToken, Injector injector, String env) {
        MediaType JSON
                = MediaType.get("application/x-kryo-v2");
        OkHttpClient client = new OkHttpClient();
        kryoSerializer = injector.getInstance(Key.get(KryoSerializer.class, Names.named("referenceFalseKryoSerializer")));
        RequestBody body = RequestBody.create(kryoSerializer.asBytes(delegateTaskResponse), JSON);
        Request request1 = new Request.Builder()
                .url("https://"+env+"/api/agent/tasks/" + delegateTaskId + "/delegates/" + delegateId + "/v2?accountId=" + accountId)
                .post(body)
                .header("Authorization","Delegate "+strToken)
                .build();
        try (Response response = client.newCall(request1).execute()) {
            return response.body().string();
        } catch (Exception ex) {
           log.error("HTTP call got failed : {}",ex);
        }
        return null;
    }
}
