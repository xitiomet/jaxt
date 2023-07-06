var term;
var fitAddon = new FitAddon.FitAddon();
var command = '';
var connection;
var debugMode = false;
var reconnectTimeout;
var hostname = location.hostname;
var protocol = location.protocol;
var port = location.port;
var wsProtocol = 'ws';
var httpUrl = '';
var termAuth = "";
var sourceCallsign = "";
var jaxtHostname = "";
var runningApp = undefined;
var playingAudio = null;

function getParameterByName(name, url = window.location.href) 
{
    name = name.replace(/[\[\]]/g, '\\$&');
    var regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
        results = regex.exec(url);
    if (!results) return null;
    if (!results[2]) return '';
    return decodeURIComponent(results[2].replace(/\+/g, ' '));
}

function fitStuff()
{
    var terminalElement = document.getElementById('terminal');
    terminalElement.style.height = window.innerHeight + 'px';
    fitAddon.fit();
}

window.onload = function() {
    termAuth = getParameterByName('termAuth');
    if (termAuth != null)
    {
        login();
    }
};

function login()
{
    term = new Terminal({cursorBlink: true, allowProposedApi: true});
    term.open(document.getElementById('terminal')); 
    term.loadAddon(fitAddon);
    fitStuff();
    setupWebsocket();
}

window.onresize = function() {
    fitStuff();
}

function prompt(term)
{
    command = '';
    if (sourceCallsign == "")
       term.write('\r\n\x1B[0;91m@' + jaxtHostname + '\x1B[0m$ ');
    else
       term.write('\r\n\x1B[0;93m' + sourceCallsign + '\x1B[0;91m@' + jaxtHostname + '\x1B[0m$ ');
}

function promptLength()
{
    return sourceCallsign.length + jaxtHostname.length + 3;
}

var commands = {
    help: {
      f: (args) => {
        term.writeln([
          'JAXT Shell help',
          ...Object.keys(commands).map(e => `  ${e.padEnd(10)} ${ commands[e].description.split('\r\n').join('\r\n             ') }`)
        ].join('\n\r\r\n'));
        prompt(term);
      },
      description: 'Prints this help message',
    },
    ui: {
      f: (args) => {
        if (sourceCallsign == "")
        {
            term.writeln("ERROR: you must set your callsign first by using the \"source\" command");
        } else {
            if (args.length >= 2)
            {
                var destinationCallsign = args[0].toUpperCase();
                var payload = args.splice(1).join(' ');
                var packet = {
                    "source": sourceCallsign,
                    "destination": destinationCallsign,
                    "control": ["UI","C"],
                    "payload": payload
                };
                sendEvent(packet);
                term.writeln("Sending UI frame to TNC:");
                term.writeln(sourceCallsign + " > " + destinationCallsign + ": " + payload);
            } else {
                term.writeln("ERROR: not enough parameters");
            }
        }
        prompt(term);
      },
      description: 'Send a UI frame to TNC. First parameter is the target callsign\r\n' +
                   'and the rest is the payload.\r\n\r\n' +
                   'Example:\r\n$ ui TARGET-1 This is some cool stuff!'
    },
    lsaudio: {
        f: (args) => {
          sendEvent({"action":"lsaudio"});
        },
        description: 'List Audio devcies',
    },
    source: {
      f: (args) => {
        if (args.length > 0)
        {
            sourceCallsign = args[0].toUpperCase();
            term.writeln("your callsign is now \"" + sourceCallsign + "\"");
        } else {
            if (sourceCallsign == "")
                term.writeln("ERROR: not enough parameters");
            else
                term.writeln("your callsign is \"" + sourceCallsign + "\"");
        }
        prompt(term);
      },
      description: 'Set your callsign. Example: $ source mycall-5'
    },
    connect: {
        f: (args) => {
            if (sourceCallsign == "")
            {
                term.writeln("ERROR: you must set your callsign first by using the \"source\" command");
            } else {
                runningApp = connectApp;
                runningApp.start(args);
            }
        },
        description: 'Connect to a remote radio terminal. Example: $ connect term-5'
    },
    listen: {
        f: (args) => {
            runningApp = listenApp;
            runningApp.start(args);
        },
        description: 'Listen to an audio device on the system running jaxt\r\nExample: "listen 2" (use lsaudio to get device number)'
    },
    exit: {
        f: (args) => {
            window.close();
        },
        description: 'Exit this terminal'
    },
  };

const remoteApp = {
    start: (args) => {

    },
    stop: () => {
        sendEvent({"action": "kill"});
    },
    handlePacket: (packet) => {

    },
    handleCommand: (command) => {
        sendEvent({"action": "input", "text": command});
        term.writeln('');
    }
};

const listenApp = {
    start: (args) => {
        var audioUrl = "/jaxt/api/stream/?termAuth=" + encodeURIComponent(termAuth) + "&devId=" + encodeURIComponent(args[0]);
        console.log("Streaming: " + audioUrl);
        playingAudio = new Audio(audioUrl);
        playingAudio.onerror = () => {
            prompt(term);
            runningApp = null;
        }
        playingAudio.play();
        term.writeln("Streaming Audio (press ctrl-c to stop)");
    },
    stop: () => {
        try
        {
            playingAudio.pause();
            playingAudio.src = '';
        } catch (err) {
            console.log(err);
        }
    },
    handlePacket: (packet) => {

    },
    handleCommand: (command) => {
        
    }
};

function runCommand(term, text)
{
    if (runningApp == undefined || runningApp == null)
    {
        const tsplit = text.trim().split(' ');
        const command = tsplit[0];
        const args = tsplit.splice(1);
        if (command.length > 0) 
        {
            term.writeln('');
            if (command in commands) 
            {
                var commandEntry = commands[command];
                if (commandEntry.hasOwnProperty('remote'))
                {
                    if (commandEntry.remote)
                    {
                        sendEvent({"action": "command", "command": command, "args": args});
                        runningApp = remoteApp;
                        return;
                    }
                }
                commandEntry.f(args);
                return;
            }
            term.writeln(`${command}: command not found`);
        }
        prompt(term);
    } else {
        runningApp.handleCommand(text);
    }
}

function handlePacket(packet)
{
    if (packet.destination == sourceCallsign)
    {
        if ((runningApp != null && runningApp != undefined))
        {
            //console.log("App should handle packet!");
            runningApp.handlePacket(packet);
        } else {
            if (packet.control.includes('DISC'))
            {
                setTimeout(() => {
                    sendEvent({
                        "source": sourceCallsign,
                        "destination": packet.source,
                        "control": ["UA","F","R"]
                    });
                },2000);
            }
        }
    }
}

function runFakeTerminal() 
{
    if (term._initialized) {
      return;
    }
    term.write('     ____.  _____  ____  ______________\r\n');
    term.write('    |    | /  _  \\ \\   \\/  /\\__    ___/\r\n');
    term.write('    |    |/  /_\\  \\ \\     /   |    |   \r\n');
    term.write('/\\__|    /    |    \\/     \\   |    |   \r\n');
    term.write('\\________\\____|__  /___/\\  \\  |____|   \r\n');
    term.write('                 \\/      \\_/           \r\n');
    term.write('\r\n');
    term.write('Welcome to the JAXT terminal, type "help" for a list of commands.\r\n');
    if (sourceCallsign != "")
        term.write('Your callsign is set to \"' + sourceCallsign + '\"\r\n');
    else
        term.write('Your callsign is NOT set!\r\n');
    term._initialized = true;
    term.prompt = () => { prompt(term); };
    prompt(term);

    term.onData(e => {
        switch (e) {
          case '\u0003': // Ctrl+C
            term.write('^C');
            if (runningApp != null)
            {
                runningApp.stop();
                runningApp = null;
            }
            prompt(term);
            break;
          case '\r': // Enter
            runCommand(term, command);
            command = '';
            break;
          case '\u007F': // Backspace (DEL)
            // Do not delete the prompt
            if (term._core.buffer.x > promptLength()) 
            {
              term.write('\b \b');
              if (command.length > 0) 
              {
                command = command.substr(0, command.length - 1);
              }
            }
            break;
          case '\t':
            if (!command.includes(' '))
            {
                for(cmd of Object.keys(commands))
                {
                    if (cmd.startsWith(command))
                    {
                        var finishCmd = cmd.substr(command.length);
                        term.write(finishCmd);
                        command += finishCmd;
                    }
                }
            }
            break;
          default: // Print all other characters for demo
            if (e >= String.fromCharCode(0x20) && e <= String.fromCharCode(0x7E) || e >= '\u00a0') {
              command += e;
              term.write(e);
            }
        }
      });
  
    runFakeTerminal();
}

function sendEvent(wsEvent)
{
    var out_event = JSON.stringify(wsEvent);
    if (debugMode)
        console.log("Transmit: " + out_event);
    try
    {
        connection.send(out_event);
    } catch (err) {
        console.log(err);
    }
}

function doAuth()
{
    sendEvent({
        "termAuth": termAuth,
        "termId": Date.now()
    });
}

function setupWebsocket()
{
    try
    {
        if (hostname == '')
        {
            debugMode = true;
            hostname = '127.0.0.1';
            protocol = 'http';
            port = 8101;
            httpUrl = "http://127.0.0.1:8101/";
        }
        if (protocol.startsWith('https'))
        {
            wsProtocol = 'wss';
        }
        connection = new WebSocket(wsProtocol + '://' + hostname + ':' + port + '/jaxt/');
        
        connection.onopen = function () {
                term.writeln('WebSocket connected!');
                doAuth();
        };
        
        connection.onerror = function (error) {
        };

        //Code for handling incoming Websocket messages from the server
        connection.onmessage = function (e) {
            if (debugMode)
            {
                console.log("Receive: " + e.data);
            }
            var jsonObject = JSON.parse(e.data);
            if (jsonObject.hasOwnProperty("action"))
            {
                var action = jsonObject.action;
                if (action == 'authOk') {
                    sourceCallsign = jsonObject.source;
                    if (sourceCallsign == null)
                        sourceCallsign = "";
                    jaxtHostname = jsonObject.hostname;
                    runFakeTerminal();
                } else if (action == 'lsaudio') {
                    var a = 0;
                    term.writeln("-- Input --");
                    for(devname of jsonObject.recording)
                    {
                        term.write(a + ": " + devname);
                        if (jsonObject.activeRecording.hasOwnProperty(devname))
                        {
                            term.write(" \x1B[0;92m(Active)\x1B[0m");
                            if (jsonObject.activeRecording[devname]['autoRecord'] == true)
                            {
                                term.write(" \x1B[0;91m(A-REC)\x1B[0m");
                            }
                        }
                        term.writeln("");
                        a++;
                    }
                    term.writeln("");
                    term.writeln("-- Output --");
                    var b = 0;
                    for(devname of jsonObject.playback)
                    {
                        term.write(b + ": " + devname);
                        if (jsonObject.activePlayback.hasOwnProperty(devname))
                        {
                            term.write(" \x1B[0;92m(Active)\x1B[0m");
                        }
                        term.writeln("");
                        b++;
                    }
                    prompt(term);
                } else if (action == 'authFail') {
                    document.getElementById('errorMsg').innerHTML = jsonObject.error;
                } else if (action == 'kissConnected') {
                    //term.writeln("WARNING: KISS Connected");
                } else if (action == 'kissDisconnected') {
                    //term.writeln("WARNING: Disconnected");
                } else if (action == 'write') {
                    term.write(jsonObject.data);
                } else if (action == 'commands') {
                    Object.entries(jsonObject.commands).forEach((entry) => {
                        const [key, value] = entry;
                        if (value.hasOwnProperty('execute'))
                            value.remote = true;
                        commands[key] = value;
                    });
                } else if (action == 'prompt') {
                    if (runningApp != null)
                    {
                        runningApp.stop();
                        runningApp = null;
                    }
                    prompt(term);
                }
            } else if (jsonObject.hasOwnProperty("source") && jsonObject.hasOwnProperty("destination") && jsonObject.hasOwnProperty("control")) {
                handlePacket(jsonObject);
            }
        };
        
        connection.onclose = function () 
        {
            term.writeln('WARNING: WebSocket connection lost');
            reconnectTimeout = setTimeout(setupWebsocket, 10000);
        };
    } catch (err) {
        console.log(err);
    }
}

function getDTString(date)
{
    var now = new Date();
    var nowDateString = now.toLocaleDateString();
    var dateString = date.toLocaleDateString();
    var timeString = date.toLocaleTimeString();
    if (nowDateString != dateString)
    {
        return dateString + ' ' + timeString;
    } else {
        return timeString;
    }
}