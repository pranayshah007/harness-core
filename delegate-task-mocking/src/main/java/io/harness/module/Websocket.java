package io.harness.module;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.security.TokenGenerator;
import io.harness.security.X509TrustManagerBuilder;

import io.harness.taskResponse.TaskResponse;
import io.harness.threading.ThreadPool;
import io.harness.util.DataConfiguration;
import io.harness.util.DelegateTaskExecutionData;
import io.harness.util.RequestCall;

import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.asynchttpclient.AsyncHttpClient;

import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.atmosphere.wasync.*;
import org.atmosphere.wasync.transport.TransportNotSupported;

import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.UnexpectedException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Future;


@Slf4j
public class Websocket  {
    private static final String TASK_EVENT_MARKER = "{\"eventType\":\"DelegateTaskEvent\"";
    private static final String ABORT_EVENT_MARKER = "{\"eventType\":\"DelegateTaskAbortEvent\"";
    private static final String HEARTBEAT_RESPONSE = "{\"eventType\":\"DelegateHeartbeatResponseStreaming\"";
    private final Set<String> currentlyAcquiringTasks = ConcurrentHashMap.newKeySet();
    static Client client  = org.atmosphere.wasync.ClientFactory.getDefault().newClient();
    private Socket socket;
    private Gson gson ;
    private HttpPut httpput = null;
    private HttpPost httppost = null;
    HttpClient httpClient = null;

    private String body = "";
    private String accountId;
    private String env;
    private String delegateId;
    private String delegateTokenName= "default_token";
    private String delegateConnectionId;
    private String version;
    private String accountSecret;
    private ByteArrayEntity byteArrayEntity = null;
    private StringEntity stringEntity = null;
    private TokenGenerator tokenGenerator = null;

    private AsyncHttpClient asyncHttpClient;
    private HeartBeat heartBeat=null;
    private RequestCall request = new RequestCall();
    private Injector injector;
    private DelegateTaskResponse delegateTaskResponse = null;

    private TaskResponse taskResponse = null;
    private final ThreadPoolExecutor threadPoolExecutor = ThreadPool.create(10, 400, 1, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("task-exec-%d").setPriority(Thread.MIN_PRIORITY).build());

    private ScheduledExecutorService healthMonitorExecutor = new ScheduledThreadPoolExecutor(
            1, new ThreadFactoryBuilder().setNameFormat("healthMonitor-%d").setPriority(Thread.MAX_PRIORITY).build());
    private final Map<String, DelegateTaskEvent> currentlyExecutingFutures = new ConcurrentHashMap<>();
    private int waitTime = new Random().nextInt(10 - 0 + 1) + 0;
    public Websocket(DataConfiguration delegateData,  int delegateIdGen, String uuid, Injector injector) {
        this.accountId=delegateData.getAccountId();
        this.delegateId=delegateData.getDelegateName()+"-"+delegateIdGen;
        this.delegateConnectionId=uuid;
        this.version=delegateData.getVersion();
        this.accountSecret=delegateData.getToken();
        this.env=delegateData.getEnv();
        this.injector = injector;
        tokenGenerator = new TokenGenerator( accountId,  accountSecret);
        PoolingHttpClientConnectionManager connManager
                = new PoolingHttpClientConnectionManager();
        connManager.setDefaultMaxPerRoute(delegateData.getDelegateCount());
        httpClient = HttpClients.custom().setConnectionManager(connManager)
                .build();
    }

    public void createSocket() throws Exception{
        final TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
        final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient().trustManager(trustManager);
        asyncHttpClient = new DefaultAsyncHttpClient(
                new DefaultAsyncHttpClientConfig.Builder().setUseProxyProperties(true).setSslContext(sslContextBuilder.build()).build());

        RequestBuilder requestBuilder = prepareRequestBuilder(accountId, delegateId, delegateTokenName, delegateConnectionId, version);
        Options clientOptions = client.newOptionsBuilder()
                .runtime(asyncHttpClient, true)
                .reconnect(true)
                .reconnectAttempts(Integer.MAX_VALUE)
                .pauseBeforeReconnectInSeconds(5)
                .build();
        socket = client.create(clientOptions);
        heartBeat = new HeartBeat(accountId, delegateId, delegateTokenName, delegateConnectionId, accountSecret, socket);
        socket
                .on(Event.MESSAGE,
                        new Function<String>() { // Do not change this, wasync doesn't like lambdas
                            @Override
                            public void on(String message) {
                                System.out.println("Message received = " + message+" - "+ delegateId);
                                handleMessageSubmit(message);
                            }
                        })
                .on(Event.ERROR,
                        new Function<Exception>() { // Do not change this, wasync doesn't like lambdas
                            @Override
                            public void on(Exception e) {
                                System.out.println("Exception on websocket = " + e.getMessage() +" - "+ delegateId);
                            }
                        })
                .on(Event.OPEN,
                        new Function<Object>() { // Do not change this, wasync doesn't like lambdas
                            @Override
                            public void on(Object o) {
                                System.out.println("Channel open " + delegateId);
                            }
                        })
                .on(Event.CLOSE,
                        new Function<Object>() { // Do not change this, wasync doesn't like lambdas
                            @Override
                            public void on(Object o) {
                                System.out.println("Channel close " + delegateId);
                            }
                        })
                .on(new Function<IOException>() {
                    @Override
                    public void on(IOException ioe) {
                        System.out.println("Error occured while starting Delegate = " + ioe.getMessage()+" - "+ delegateId);
                    }
                })
                .on(new Function<TransportNotSupported>() {
                    public void on(TransportNotSupported ex) {
                        System.out.println("Connection was terminated forcefully (most likely), trying to reconnect = " + ex+" - "+ delegateId);
                    }
                });

        socket.open(requestBuilder.build());
        String connectionTimeInMin = System.getenv("CONNECTION_TIME_IN_MIN");
        int round = connectionTimeInMin!=null?Integer.parseInt(connectionTimeInMin):10000;
        startHeartbeat(accountId, delegateId, delegateTokenName, delegateConnectionId);
        Thread.sleep(round*60000);
        socket.close();
        System.exit(0);
    }

    private void startHeartbeat(String accountId, String delegateId, String delegateTokenName, String delegateConnectionId) {
        healthMonitorExecutor.scheduleAtFixedRate(() -> {
            try {
                System.out.println("HeartBeat going" +" - "+ delegateId);
                heartBeat.sendHeartbeat(accountId, delegateId, delegateTokenName, delegateConnectionId);
            } catch (Exception ex) {
                log.error("Exception while sending heartbeat", ex);
            }
        }, 0, 50000, TimeUnit.MILLISECONDS);
    }

    private void handleMessageSubmit(String message) {
        DelegateTaskEvent delegateTaskEvent;
        if (StringUtils.startsWith(message, TASK_EVENT_MARKER)) {
            try {
                gson = new Gson();
                delegateTaskEvent = gson.fromJson(message, DelegateTaskEvent.class);
                if (delegateTaskEvent.getDelegateTaskId() == null) {
                    log.warn("Delegate task id cannot be null");
                    return;
                }
                if (currentlyExecutingFutures.containsKey(delegateTaskEvent.getDelegateTaskId())) {
                    log.info("Task [DelegateTaskEvent: {}] already queued, dropping this request ", delegateTaskEvent);
                    return;
                }
                DelegateTaskExecutionData taskExecutionData = DelegateTaskExecutionData.builder().build();

                if (currentlyExecutingFutures.putIfAbsent(delegateTaskEvent.getDelegateTaskId(), delegateTaskEvent) == null) {
                    final Future<?> taskFuture = threadPoolExecutor.submit(() -> dispatchDelegateTaskAsync(delegateTaskEvent));
                    taskExecutionData.setTaskFuture(taskFuture);
                    return;
                }
                return;
            } catch (Exception ex) {
                log.error("Exception in handling websocket message for task acquiring and execution {}", ex);
            }
        }
    }

    private void dispatchDelegateTaskAsync(DelegateTaskEvent delegateTaskEvent) {
        final String delegateTaskId = delegateTaskEvent.getDelegateTaskId();
        String strToken = tokenGenerator.getToken("https", "localhost", 9090, "test-0");
        if (delegateTaskId == null) {
            log.warn("Delegate task id cannot be null");
            return;
        }
        if (currentlyAcquiringTasks.contains(delegateTaskId)) {
            log.info("Task [DelegateTaskEvent: {}] currently acquiring. Don't acquire again",delegateTaskEvent);
            return;
        }
        try {
        currentlyAcquiringTasks.add(delegateTaskId);


        //executeTask(delegateTaskEvent);
        System.out.println("threadPoolExecutor.getCompletedTaskCount() : " + threadPoolExecutor.getCompletedTaskCount());
    } catch (Exception ex) {
        log.error("Exception while acquiring task {}",ex);
    } finally {
        currentlyAcquiringTasks.remove(delegateTaskId);
        currentlyExecutingFutures.remove(delegateTaskId);
    }
    }

//    private void executeTask(DelegateTaskEvent delegateTaskEvent) throws Exception{
//        taskResponse = new TaskResponse();
//        final String delegateTaskId = delegateTaskEvent.getDelegateTaskId();
//        String strToken = tokenGenerator.getToken("https", "localhost", 9090, "test-0");
//        delegateTaskResponse = taskResponse.getTaskResponse(delegateTaskEvent, delegateId);
//        Thread.sleep(waitTime*1000);
//        if (delegateTaskResponse != null) {
//            request.executeTask(delegateTaskResponse,delegateTaskId,delegateId,accountId,strToken, injector, env);
//        }
//    }

    private RequestBuilder prepareRequestBuilder (String accountId, String delegateId, String
            delegateTokenName, String delegateConnectionId, String version) throws Exception {
        try {
            String strToken = tokenGenerator.getToken("https", "localhost", 8181, "test-0");
            URIBuilder uriBuilder =
                    new URIBuilder("https://"+env+ "/stream/" + "delegate/" + accountId)
                            .addParameter("delegateId", delegateId)
                            .addParameter("delegateTokenName", delegateTokenName)
                            .addParameter("delegateConnectionId", delegateConnectionId)
                            .addParameter("token", strToken)
                            .addParameter("sequenceNum", null)
                            .addParameter("delegateToken", null)
                            .addParameter("version", version);

            URI uri = uriBuilder.build();

            // Stream the request body
            final RequestBuilder requestBuilder = client.newRequestBuilder().method(Request.METHOD.GET).uri(uri.toString());
            String str = tokenGenerator.getToken("https", "localhost", 9090, "del-new-automation-0");
            requestBuilder.transport(Request.TRANSPORT.WEBSOCKET);
            // send accountId + delegateId as header for delegate gateway to log websocket connection with account.
            requestBuilder.header("accountId", accountId);
            final String agent = "delegate/" + version;
            requestBuilder.header("User-Agent", agent);
            requestBuilder.header("delegateId", delegateId);

            return requestBuilder;
        } catch (URISyntaxException e) {
            throw new UnexpectedException("Unable to prepare uri", e);
        }
    }
}

