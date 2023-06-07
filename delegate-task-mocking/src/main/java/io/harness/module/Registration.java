package io.harness.module;



import com.google.inject.Inject;
import com.google.inject.Injector;


import io.harness.security.TokenGenerator;

import io.harness.util.DataConfiguration;


import io.harness.util.OKHttpClientMock;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import okhttp3.*;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.fasterxml.uuid.Generators.timeBasedGenerator;
@Slf4j
public class Registration implements Runnable{
    private String env;
    private String accountId;
    private String delegateName;
    private String accountSecret;
    private String version;
    private DataConfiguration delegateData;
    private int count;
    private HttpPost httppost = null;
    private String requestBody = "";
    private TokenGenerator tokenGenerator = null;
    private String strToken = "";
    private HttpClient httpClient = null;
    private StringEntity stringEntity = null;
    private HttpResponse response = null;
    private Websocket websocket = null;


    private Injector injector;
    public Registration(DataConfiguration delegateData, Injector injector) {
        this.delegateData = delegateData;
        this.injector = injector;
        setup();
    }
    private void setup(){
        this.env = delegateData.getEnv();
        this.accountId = delegateData.getAccountId();
        this.delegateName = delegateData.getDelegateName();
        this.accountSecret = delegateData.getToken();
        this.count = delegateData.getDelegateCount();
        this.version = delegateData.getVersion();
        PoolingHttpClientConnectionManager connManager
                = new PoolingHttpClientConnectionManager();
        connManager.setDefaultMaxPerRoute(count);

        httpClient = HttpClients.custom().setConnectionManager(connManager)
                .build();
        tokenGenerator = new TokenGenerator( accountId,  accountSecret);
        strToken = tokenGenerator.getToken("https", "localhost", 9090, "test-0");

    }

    public boolean doRegistration(int n) throws  Exception {
         requestBody = "{  \n" +
                "    \"delegateId\": \"" + delegateName + "-" + n + "\",\n" +
                "    \"accountId\" : \"" + accountId + "\",\n" +
                "    \"hostName\": \"" + delegateName + "-" + n + "-delegate" + "-0" + "\",\n" +
                "    \"delegateName\" : \"" + delegateName + "-" + n + "-delegate" + "\",\n" +
                "    \"delegateTokenName\" : \"delegate_token\",\n" +
                "    \"version\" : \""+version+"\",\n" +
                "    \"status\" : \"ENABLED\",\n" +
                "    \"delegateType\" : \"KUBERNETES\",\n" +
                "    \"supportedTaskTypes\" : [\"SHELL_SCRIPT_TASK_NG\",\"SHELL_SCRIPT_TASK\",\"HTTP\"],\n" +
                "    \"ng\" : \"TRUE\"\n" +
                "}";
        MediaType JSON
                = MediaType.get("application/json");
        OkHttpClient client = OKHttpClientMock.getUnsafeOkHttpClient();
        RequestBody body = RequestBody.create(requestBody, JSON);
        Request request1 = new Request.Builder()
                .url("https://"+env+"/gateway/api/agent/delegates/register?accountId=" + accountId)
                .post(body)
                .header("Authorization", "Delegate " + strToken)
                .build();
        try (Response response = client.newCall(request1).execute()) {
            if (response.code() >= 400) {
               log.warn("Registration didn't happen for {}",delegateName);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("Exception while registering delegate {} : {}",delegateName, ex);
        }
        return false;
    }
    public static String generateTimeBasedUuid() {
        UUID uuid = timeBasedGenerator().generate();
        return convertToBase64String(uuid);
    }

    protected static String convertToBase64String(UUID uuid) {
        byte[] bytes = new byte[16];
        ByteBuffer uuidBytes = ByteBuffer.wrap(bytes);
        uuidBytes.putLong(uuid.getMostSignificantBits());
        uuidBytes.putLong(uuid.getLeastSignificantBits());
        return Base64.encodeBase64URLSafeString(bytes);
    }

    @Override
    public void run() {
        try {
            int delegateIdGen = ThreadLocalRandom.current().nextInt(1, count*10000); // generate random value based on lower and upper bound inputs from main
            if (doRegistration(delegateIdGen)) {
                Thread.sleep(10000);
                String uuid =generateTimeBasedUuid();
                websocket = new Websocket( delegateData, delegateIdGen, uuid, injector);
                websocket.createSocket();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

