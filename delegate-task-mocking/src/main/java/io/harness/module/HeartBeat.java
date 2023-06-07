package io.harness.module;

import io.harness.security.TokenGenerator;
import org.atmosphere.wasync.Socket;

public class HeartBeat {
    private String accountId="";
    private String delegateId="";
    private String delegateTokenName="";
    private String delegateConnectionId="";
    private Socket socket = null;
    private  String accountSecret="";
    private TokenGenerator tokenGenerator = null;
    HeartBeat(String accountId, String delegateId, String delegateTokenName, String delegateConnectionId, String accountSecret, Socket socket){
        this.accountId=accountId;
        this.delegateId=delegateId;
        this.delegateConnectionId=delegateConnectionId;
        this.delegateTokenName=delegateTokenName;
        this.accountSecret=accountSecret;
        this.socket=socket;
        tokenGenerator = new TokenGenerator( accountId,  accountSecret);
    }
    public void sendHeartbeat(String accountId, String delegateId, String delegateTokenName, String delegateConnectionId) throws  Exception{
        String str = tokenGenerator.getToken("https", "localhost", 8181, "test-0");
        try{
            socket.fire("{\n" +
                    "  \"delegateId\": \""+delegateId+"\",\n" +
                    "  \"accountId\": \""+accountId+"\",\n" +
                    "  \"tokenName\": \""+delegateTokenName+"\",\n" +
                    "  \"version\": \""+"1.0.79310"+"\",\n" +
                    "  \"lastHeartBeat\": \""+System.currentTimeMillis()+"\",\n" +
                    "  \"delegateConnectionId\": \""+delegateConnectionId+"\",\n" +
                    "  \"token\": \""+str+
                    "\"}");
        }
        catch (Exception ex){
            System.out.println("Error sending heartbeat"+" - "+ delegateId);
        }
    }
}

