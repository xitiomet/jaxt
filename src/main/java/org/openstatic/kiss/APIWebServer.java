package org.openstatic.kiss;


import org.json.*;
import org.openstatic.aprs.parser.APRSPacket;
import org.openstatic.aprs.parser.APRSTypes;
import org.openstatic.aprs.parser.Digipeater;
import org.openstatic.aprs.parser.InformationField;
import org.openstatic.aprs.parser.ObjectPacket;
import org.openstatic.aprs.parser.Parser;
import org.openstatic.aprs.parser.Position;
import org.openstatic.aprs.parser.PositionPacket;
import org.openstatic.sound.SoundSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Arrays;

import javax.servlet.AsyncContext;
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
import org.eclipse.jetty.util.ajax.JSON;

public class APIWebServer implements AX25PacketListener, Runnable
{
    private Server httpServer;
    protected ArrayList<WebSocketSession> wsSessions;
    protected HashMap<WebSocketSession, JSONObject> sessionProps;
    protected HashMap<WebSocketSession, APITermProcessHandler> processes;
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
        this.processes = new HashMap<WebSocketSession, APITermProcessHandler>();
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

    public static synchronized String generateBigAlphaKey(int key_length)
    {
        try
        {
            // make sure we never get the same millis!
            Thread.sleep(1);
        } catch (Exception e) {}
        Random n = new Random(System.currentTimeMillis());
        String alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuffer return_key = new StringBuffer();
        for (int i = 0; i < key_length; i++)
        {
            return_key.append(alpha.charAt(n.nextInt(alpha.length())));
        }
        String randKey = return_key.toString();
        //System.err.println("Generated Rule ID: " + randKey);
        return randKey;
    }

    public void addHistory(JSONObject obj)
    {
        this.packetHistory.add(obj);
        if (this.packetHistory.size() > 1000)
            this.packetHistory.remove(0);
    }

    public static void sendAuthOk(WebSocketSession session, String termAuth)
    {
        JSONObject authJsonObject = new JSONObject();
        authJsonObject.put("action", "authOk");
        authJsonObject.put("termAuth", termAuth);
        authJsonObject.put("kissConnected", APIWebServer.instance.kClient.isConnected());
        authJsonObject.put("txDisabled", JavaKISSMain.settings.optBoolean("txDisabled", false));
        authJsonObject.put("availableHistory", APIWebServer.instance.packetHistory.size());
        authJsonObject.put("hostname", JavaKISSMain.settings.optString("hostname", "JAXT"));
        authJsonObject.put("source", JavaKISSMain.settings.optString("source", null));
        session.getRemote().sendStringByFuture(authJsonObject.toString());
    }

    public void handleWebSocketEvent(JSONObject j, WebSocketSession session) 
    {
        JSONObject sessionProperties = this.sessionProps.get(session);
        if (!sessionProperties.optBoolean("auth", false))
        {
            String settingPassword = JavaKISSMain.settings.optString("apiPassword","");
            if (j.has("apiPassword"))
            {
                boolean authGood = settingPassword.equals(j.optString("apiPassword",""));
                if (authGood)
                {
                    String termAuth = generateBigAlphaKey(16);
                    sessionProperties.put("auth", true);
                    sessionProperties.put("termAuth", termAuth);
                    sendAuthOk(session,termAuth);
                } else {
                    JSONObject errorJsonObject = new JSONObject();
                    errorJsonObject.put("action", "authFail");
                    errorJsonObject.put("error", "Invalid apiPassword!");
                    session.getRemote().sendStringByFuture(errorJsonObject.toString());
                }
            } else if (j.has("termAuth")) {
                String termAuth = j.optString("termAuth", "");
                if (validateTermAuth(termAuth))
                {
                    sessionProperties.put("auth", true);
                    sessionProperties.put("termAuth", termAuth);
                    sendAuthOk(session,termAuth);
                }
            }
        }
        
        if (sessionProperties.optBoolean("auth", false))
        {
            if (j.has("source") && j.has("destination") && j.has("control"))
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
            } else if (j.has("termId")) {
                sessionProperties.put("termId", j.optLong("termId", 0));
                JSONObject commandsObject = new JSONObject();
                commandsObject.put("action", "commands");
                if (JavaKISSMain.settings.has("commandsFile"))
                {
                    try
                    {
                        JSONObject commands = JavaKISSMain.loadJSONObject(new File(JavaKISSMain.settings.optString("commandsFile")));
                        commandsObject.put("commands", commands);
                        session.getRemote().sendStringByFuture(commandsObject.toString());
                        sessionProperties.put("commands", commands);
                    } catch (Exception cexc) {}
                }
            } else if (j.has("action")) {
                String action = j.optString("action","");
                if (action.equals("command") && sessionProperties.has("termId"))
                {
                    JSONArray argJSONArray = j.optJSONArray("args");
                    String[] args = JSONArrayToStringArray(argJSONArray);
                    handleCommand(session, sessionProperties, j.optString("command"), args);
                } else if (action.equals("input") && sessionProperties.has("termId")) {
                    APITermProcessHandler apiTermProcessHandler = this.processes.get(session);
                    if (apiTermProcessHandler != null)
                    {
                        apiTermProcessHandler.println(j.optString("text",""));
                    }
                } else if (action.equals("kill") && sessionProperties.has("termId")) {
                    if (this.processes.containsKey(session))
                    {
                        APITermProcessHandler apiTermProcessHandler = this.processes.get(session);
                        if (apiTermProcessHandler != null)
                        {
                            apiTermProcessHandler.kill();
                        }
                        this.processes.remove(session);
                    }
                } else if (action.equals("clearHistory")) {
                    this.packetHistory.clear();
                } else if (action.equals("lsaudio")) {
                    JavaKISSMain.soundSystem.refreshMixers();
                    JSONObject infoPacket = new JSONObject();
                    infoPacket.put("action", "lsaudio");
                    infoPacket.put("recording", JavaKISSMain.soundSystem.getRecordingDevices());
                    infoPacket.put("playback", JavaKISSMain.soundSystem.getPlaybackDevices());
                    infoPacket.put("activeRecording", JavaKISSMain.soundSystem.getActiveRecordingDevices());
                    infoPacket.put("activePlayback", JavaKISSMain.soundSystem.getActivePlaybackDevices());
                    infoPacket.put("timestamp", System.currentTimeMillis());
                    session.getRemote().sendStringByFuture(infoPacket.toString());
                } else if (action.equals("info")) {
                    broadcastINFO(j.optString("text", ""));
                }
            }
        } else {
            JSONObject errorJsonObject = new JSONObject();
            errorJsonObject.put("error", "Not Authorized!");
            session.getRemote().sendStringByFuture(errorJsonObject.toString());
        }
        this.sessionProps.put(session, sessionProperties);
    }

    public boolean validateTermAuth(String termAuth)
    {
        if (termAuth == null) return false;
        List<String> termAuths = this.sessionProps.values().stream().map((p) -> p.optString("termAuth", null)).filter((v) -> { return !"".equals(v) && v != null; }).collect(Collectors.toList());
        return termAuths.contains(termAuth);
    }

    private static String[] JSONArrayToStringArray(JSONArray arry)
    {
        String[] args = new String[arry.length()];
        for (int i = 0; i < arry.length(); i++)
        {
            args[i] = arry.getString(i);
        }
        return args;
    }

    public void broadcastINFO(String text)
    {
        JSONObject infoPacket = new JSONObject();
        infoPacket.put("action", "info");
        infoPacket.put("text", text);
        infoPacket.put("timestamp", System.currentTimeMillis());
        APIWebServer.instance.broadcastJSONObject(infoPacket);
        addHistory(infoPacket);
    }

    public void broadcastJSONObject(JSONObject jo) 
    {
        String message = jo.toString();
        for (Session s : this.wsSessions)
        {
            try
            {
                JSONObject sessionProps = this.sessionProps.get(s);
                if (sessionProps.optBoolean("auth", false))
                {
                    s.getRemote().sendStringByFuture(message);
                }
            } catch (Exception e) {

            }
        }
    }

    public void sendJSONObject(JSONObject jo, long termId) 
    {
        String message = jo.toString();
        for (Session s : this.wsSessions) 
        {
            try
            {
                JSONObject sessionProps = this.sessionProps.get(s);
                if (sessionProps.optBoolean("auth", false) && sessionProps.optLong("termId", 0) == termId)
                {
                    s.getRemote().sendStringByFuture(message);
                }
            } catch (Exception e) {

            }
        }
    }

    public void handleCommand(WebSocketSession session, JSONObject sessionProperties, String command, String[] args)
    {
        long termId = sessionProperties.optLong("termId", 0);
        JSONObject commands = sessionProperties.optJSONObject("commands", new JSONObject());
        if (commands.has(command)) {
            JSONObject commandObject = commands.getJSONObject(command);
            ArrayList<String> commandWithArgs = new ArrayList<String>();
            if (commandObject.has("execute"))
            {
                String[] execute = JSONArrayToStringArray(commandObject.getJSONArray("execute"));
                commandWithArgs.addAll(Arrays.asList(execute));
                if (!commandObject.optBoolean("ignoreExtraArgs", false))
                {
                    commandWithArgs.addAll(Arrays.asList(args));
                }
                ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
                APITermProcessHandler apiTermProcessHandler = new APITermProcessHandler(termId, pb);
                this.processes.put(session, apiTermProcessHandler);
            } else {
                writeTerm(termId, command + ": invalid command entry\r\n");
                promptTerm(termId);
            }
        } else {
            writeTerm(termId, command + ": command not found\r\n");
            promptTerm(termId);
        }
    }

    public void writeTerm(long termId, String text)
    {
        JSONObject jo = new JSONObject();
        jo.put("action", "write");
        jo.put("data", text);
        sendJSONObject(jo, termId);
    }

    public void promptTerm(long termId)
    {
        JSONObject jo = new JSONObject();
        jo.put("action", "prompt");
        sendJSONObject(jo, termId);
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
                JSONObject sessionProperties = new JSONObject();
                String settingPassword = JavaKISSMain.settings.optString("apiPassword","");

                if (settingPassword.equals(""))
                {
                    String termAuth = generateBigAlphaKey(16);
                    sessionProperties.put("auth", true);
                    sessionProperties.put("termAuth", termAuth);
                    APIWebServer.sendAuthOk(wssession, termAuth);
                } else {
                    JSONObject hostJsonObject = new JSONObject();
                    hostJsonObject.put("hostname", JavaKISSMain.settings.optString("hostname", "JAXT"));
                    session.getRemote().sendStringByFuture(hostJsonObject.toString());
                }
                APIWebServer.instance.sessionProps.put(wssession, sessionProperties);
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
                if (APIWebServer.instance.processes.containsKey(wssession))
                {
                    APITermProcessHandler proc = APIWebServer.instance.processes.get(wssession);
                    proc.kill();
                    APIWebServer.instance.processes.remove(wssession);
                }
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
                    if (JavaKISSMain.settings.optString("apiPassword","").equals(requestPost.optString("apiPassword","")) || APIWebServer.instance.validateTermAuth(requestPost.optString("termAuth",null)))
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
                        response.put("error", "Invalid apiPassword or termAuth!");
                    }
                    httpServletResponse.getWriter().println(response.toString());
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse)
                throws ServletException, IOException {
            String responseType = "text/javascript";
            String target = request.getPathInfo();

            if (target.startsWith("/logs/") && JavaKISSMain.logsFolder != null)
            {
                target = target.substring(5);
                //System.err.println(target + " " + File.separator);
                File logFile = new File(JavaKISSMain.logsFolder, target.replace('/', File.separatorChar));
                //System.err.println(logFile.toString());
                if (logFile.exists() && !logFile.isDirectory())
                {
                    String contentType = InterfaceServlet.getContentTypeFor(target);            
                    httpServletResponse.setContentType(contentType);
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    httpServletResponse.setCharacterEncoding("UTF-8");
                    InputStream inputStream = new FileInputStream(logFile);
                    OutputStream output = httpServletResponse.getOutputStream();
                    inputStream.transferTo(output);
                    output.flush();
                    inputStream.close();
                } else {
                    //JavaKISSMain.logAppend("interface.log", "GET " + target + " 404");
                    httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                return;
            }
            //System.err.println("Path: " + target);
            Set<String> parameterNames = request.getParameterMap().keySet();
            JSONObject response = new JSONObject();
            if (JavaKISSMain.settings.optString("apiPassword","").equals(request.getParameter("apiPassword")) || APIWebServer.instance.validateTermAuth(request.getParameter("termAuth")))
            {
                if (target.equals("/transmit/"))
                {
                    if (APIWebServer.instance.kClient.isConnected())
                    {
                        try 
                        {
                            AX25Packet packet = AX25Packet.buildPacket(request.getParameter("source"), request.getParameter("destination"), request.getParameter("payload"));
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
                } else if (target.equals("/audio/")) {
                    response.put("recording", JavaKISSMain.soundSystem.getRecordingDevices());
                    response.put("playback", JavaKISSMain.soundSystem.getPlaybackDevices());
                    response.put("activeRecording", JavaKISSMain.soundSystem.getActiveRecordingDevices());
                    response.put("activePlayback", JavaKISSMain.soundSystem.getActivePlaybackDevices());
                } else if (target.equals("/stream/")) {
                    int devId = Integer.valueOf(request.getParameter("devId")).intValue();
                    try
                    {
                        JavaKISSMain.soundSystem.openRecordingDeviceAndWriteTo(devId, request, httpServletResponse);
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                    }
                    return;
                }
            } else {
                response.put("error", "Invalid apiPassword!");
            }
            if ("text/javascript".equals(responseType))
            {
                httpServletResponse.setContentType("text/javascript");
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                httpServletResponse.setCharacterEncoding("iso-8859-1");
                httpServletResponse.getWriter().println(response.toString());
            }
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
        broadcastJSONObject(jPacket);
        addHistory(jPacket);
        broadcastAPRS(packet);
    }

    public void broadcastAPRS(AX25Packet packet)
    {
        if (packet.controlContains("UI"))
        {
            try
            {
                ArrayList<Digipeater> digis = new ArrayList<Digipeater>();
                String[] paths = packet.getPath();
                for(int i = 0; i < paths.length; i++)
                {
                    digis.add(new Digipeater(paths[i]));
                }
                APRSPacket aprs = Parser.parseBody(packet.getSourceCallsign(), packet.getDestinationCallsign(), digis, packet.getPayload());
                if (aprs.isAprs())
                {
                    if (aprs.getType() != APRSTypes.T_UNSPECIFIED)
                    {
                        InformationField aprsData = aprs.getAprsInformation();
                        if ( aprsData != null) 
                        {
                            JSONObject aprsJSON = new JSONObject();
                            aprsJSON.put("source", packet.getSourceCallsign());
                            aprsJSON.put("destination", packet.getDestinationCallsign());
                            if (packet.getPath() != null)
                                aprsJSON.put("path", new JSONArray(packet.getPath()));
                            aprsJSON.put("comment", aprsData.getComment());
                            aprsJSON.put("type", aprs.getType());
                            aprsJSON.put("action", "APRS");

                            if (aprsData instanceof PositionPacket)
                            {
                                PositionPacket posData = ((PositionPacket)aprsData);
                                Position position = posData.getPosition();
                                aprsJSON.put("latitude", position.getLatitude());
                                aprsJSON.put("longitude", position.getLongitude());
                                aprsJSON.put("altitude", position.getAltitude());
                            }
                            if (aprsData instanceof ObjectPacket)
                            {
                                ObjectPacket objectPacket = ((ObjectPacket)aprsData);
                                Position position = objectPacket.getPosition();
                                aprsJSON.put("latitude", position.getLatitude());
                                aprsJSON.put("longitude", position.getLongitude());
                                aprsJSON.put("altitude", position.getAltitude());
                            }
                            broadcastJSONObject(aprsJSON);
                            addHistory(aprsJSON);
                        }
                    }
                }
            } catch (Exception e) {
                //e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void onTransmit(AX25Packet packet)
    {
        JSONObject jPacket = packet.toJSONObject();
        broadcastJSONObject(jPacket);
        addHistory(jPacket);
        broadcastAPRS(packet);
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
                Thread.sleep(10000);
            } catch (Exception e) {

            }
        }
    }

}
