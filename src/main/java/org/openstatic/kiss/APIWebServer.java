package org.openstatic.kiss;


import org.json.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class APIWebServer implements AX25PacketListener, Runnable
{
    private Server httpServer;
    protected ArrayList<WebSocketSession> wsSessions;
    protected HashMap<WebSocketSession, JSONObject> sessionProps;
    private KISSClient kClient;
    private Thread pingPongThread;
    private ArrayList<JSONObject> packetHistory;

    protected static APIWebServer instance;

    public APIWebServer(KISSClient client)
    {
        this.packetHistory = new ArrayList<JSONObject>();
        this.kClient = client;
        this.kClient.addAX25PacketListener(this);
        APIWebServer.instance = this;
        this.wsSessions = new ArrayList<WebSocketSession>();
        this.sessionProps = new HashMap<WebSocketSession, JSONObject>();
        httpServer = new Server(JavaKISSMain.settings.optInt("apiPort", 8101));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(ApiServlet.class, "/jaxt/api/*");
        context.addServlet(EventsWebSocketServlet.class, "/jaxt/*");
        try {
            context.addServlet(InterfaceServlet.class, "/*");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        httpServer.setHandler(context);
        try {
            httpServer.start();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        this.pingPongThread = new Thread(this);
        this.pingPongThread.start();
    }

    public void addHistory(JSONObject obj)
    {
        this.packetHistory.add(obj);
        if (this.packetHistory.size() > 1000)
            this.packetHistory.remove(0);
    }

    public void handleWebSocketEvent(JSONObject j, WebSocketSession session) 
    {
        JSONObject sessionProperties = this.sessionProps.get(session);
        if (!sessionProperties.optBoolean("auth", false))
        {
            if (JavaKISSMain.settings.optString("apiPassword","").equals(j.optString("apiPassword","")))
            {
                sessionProperties.put("auth", true);
                JSONObject authJsonObject = new JSONObject();
                authJsonObject.put("action", "authOk");
                authJsonObject.put("kissConnected", APIWebServer.instance.kClient.isConnected());
                authJsonObject.put("availableHistory", this.packetHistory.size());
                session.getRemote().sendStringByFuture(authJsonObject.toString());
            } else {
                JSONObject errorJsonObject = new JSONObject();
                errorJsonObject.put("action", "authFail");
                errorJsonObject.put("error", "Invalid apiPassword!");
                session.getRemote().sendStringByFuture(errorJsonObject.toString());
            }
        }
        
        if (sessionProperties.optBoolean("auth", false))
        {
            if (j.has("source") && j.has("destination") && j.has("payload"))
            {
                AX25Packet packet = new AX25Packet(j);
                try
                {
                    this.kClient.send(packet);
                } catch (Exception e) {

                }
            } else if (j.has("history")) {
                int historyRequest = j.optInt("history", 100);
                if (historyRequest > this.packetHistory.size())
                    historyRequest = this.packetHistory.size();
                for(int i = this.packetHistory.size() - historyRequest; i < this.packetHistory.size(); i++)
                {
                    String histPacket = this.packetHistory.get(i).toString();
                    session.getRemote().sendStringByFuture(histPacket);
                }
            }
        } else {
            JSONObject errorJsonObject = new JSONObject();
            errorJsonObject.put("error", "Not Authorized!");
            session.getRemote().sendStringByFuture(errorJsonObject.toString());
        }
        this.sessionProps.put(session, sessionProperties);
    }

    public void broadcastJSONObject(JSONObject jo) 
    {
        String message = jo.toString();
        for (Session s : this.wsSessions) {
            try {
                JSONObject sessionProps = this.sessionProps.get(s);
                if (sessionProps.optBoolean("auth", false))
                {
                    s.getRemote().sendStringByFuture(message);
                }
            } catch (Exception e) {

            }
        }
    }

    public static class EventsWebSocketServlet extends WebSocketServlet {
        @Override
        public void configure(WebSocketServletFactory factory) {
            // factory.getPolicy().setIdleTimeout(10000);
            factory.register(EventsWebSocket.class);
        }
    }

    @WebSocket
    public static class EventsWebSocket {

        @OnWebSocketMessage
        public void onText(Session session, String message) throws IOException {
            try {
                JSONObject jo = new JSONObject(message);
                if (session instanceof WebSocketSession) {
                    WebSocketSession wssession = (WebSocketSession) session;
                    APIWebServer.instance.handleWebSocketEvent(jo, wssession);
                } else {
                    //System.err.println("not instance of WebSocketSession");
                }
            } catch (Exception e) {
                //e.printStackTrace(System.err);
            }
        }

        @OnWebSocketConnect
        public void onConnect(Session session) throws IOException {
            //System.err.println("@OnWebSocketConnect");
            if (session instanceof WebSocketSession) {
                WebSocketSession wssession = (WebSocketSession) session;
                //System.out.println(wssession.getRemoteAddress().getHostString() + " connected!");
                APIWebServer.instance.wsSessions.add(wssession);
                APIWebServer.instance.sessionProps.put(wssession, new JSONObject());
            } else {
                //System.err.println("Not an instance of WebSocketSession");
            }
        }

        @OnWebSocketClose
        public void onClose(Session session, int status, String reason) {
            if (session instanceof WebSocketSession) {
                WebSocketSession wssession = (WebSocketSession) session;
                APIWebServer.instance.wsSessions.remove(wssession);
                APIWebServer.instance.sessionProps.remove(wssession);
            }
        }

    }

    public static class ApiServlet extends HttpServlet {
        public JSONObject readJSONObjectPOST(HttpServletRequest request) {
            StringBuffer jb = new StringBuffer();
            String line = null;
            try {
                BufferedReader reader = request.getReader();
                while ((line = reader.readLine()) != null) {
                    jb.append(line);
                }
            } catch (Exception e) {
                //e.printStackTrace(System.err);
            }

            try {
                JSONObject jsonObject = new JSONObject(jb.toString());
                return jsonObject;
            } catch (JSONException e) {
                e.printStackTrace(System.err);
                return new JSONObject();
            }
        }

        public boolean isNumber(String v) {
            try {
                Integer.parseInt(v);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse httpServletResponse)
                throws ServletException, IOException {
                    httpServletResponse.setContentType("text/javascript");
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    httpServletResponse.setCharacterEncoding("iso-8859-1");
                    String target = request.getPathInfo();
                    //System.err.println("Path: " + target);
                    JSONObject response = new JSONObject();
                    JSONObject requestPost = readJSONObjectPOST(request);
                    if (JavaKISSMain.settings.optString("apiPassword","").equals(requestPost.optString("apiPassword","")))
                    {       
                        try {
                            if (target.equals("/transmit/"))
                            {
                                AX25Packet packet = new AX25Packet(requestPost);
                                APIWebServer.instance.kClient.send(packet);
                                response.put("transmitted", packet.toJSONObject());
                            }
                        } catch (Exception x) {
                            x.printStackTrace(System.err);
                        }
                    } else {
                        response.put("error", "Invalid apiPassword!");
                    }
                    httpServletResponse.getWriter().println(response.toString());
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse)
                throws ServletException, IOException {
            httpServletResponse.setContentType("text/javascript");
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            httpServletResponse.setCharacterEncoding("iso-8859-1");
            String target = request.getPathInfo();
            //System.err.println("Path: " + target);
            Set<String> parameterNames = request.getParameterMap().keySet();
            JSONObject response = new JSONObject();
            if (JavaKISSMain.settings.optString("apiPassword","").equals(request.getParameter("apiPassword")))
            {
                
                if (target.equals("/transmit/"))
                {
                    if (APIWebServer.instance.kClient.isConnected())
                    {
                        try 
                        {
                            AX25Packet packet = new AX25Packet(request.getParameter("source"), request.getParameter("destination"), request.getParameter("payload"));
                            APIWebServer.instance.kClient.send(packet);
                            response.put("transmitted", packet.toJSONObject());
                        } catch (Exception x) {
                            //x.printStackTrace(System.err);
                            response.put("error", x.getLocalizedMessage());
                        }
                    } else {
                        response.put("error", "Not connected to KISS server!");
                    }
                } else if (target.equals("/settings/")) {
                    Set<String> keySet = JavaKISSMain.settings.keySet();
                    for(String key : keySet)
                    {
                        if (!"apiPassword".equals(key))
                        {
                            response.put(key, JavaKISSMain.settings.opt(key));
                        }
                    }
                }
            } else {
                response.put("error", "Invalid apiPassword!");
            }
            httpServletResponse.getWriter().println(response.toString());
            //request.setHandled(true);
        }
    }

    @Override
    public void onKISSConnect(InetSocketAddress socketAddress) 
    {
        JSONObject kissStateJsonObject = new JSONObject();
        kissStateJsonObject.put("action", "kissConnected");
        broadcastJSONObject(kissStateJsonObject);
    }

    @Override
    public void onKISSDisconnect(InetSocketAddress socketAddress) 
    {
        JSONObject kissStateJsonObject = new JSONObject();
        kissStateJsonObject.put("action", "kissDisconnected");
        broadcastJSONObject(kissStateJsonObject);
    }

    @Override
    public void onReceived(AX25Packet packet) 
    {
        JSONObject jPacket = packet.toJSONObject();
        jPacket.put("direction", "rx");
        broadcastJSONObject(jPacket);
        addHistory(jPacket);
    }

    @Override
    public void onTransmit(AX25Packet packet)
    {
        JSONObject jPacket = packet.toJSONObject();
        jPacket.put("direction", "tx");
        broadcastJSONObject(jPacket);
        addHistory(jPacket);
    }

    @Override
    public void run()
    {
        while(this.httpServer.isRunning())
        {
            try
            {
                JSONObject pingJSON = new JSONObject();
                pingJSON.put("action", "ping");
                pingJSON.put("timestamp", System.currentTimeMillis());
                broadcastJSONObject(pingJSON);
                Thread.sleep(60000);
            } catch (Exception e) {

            }
        }
    }

}
