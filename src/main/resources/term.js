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
var apiPassword = "";
var sourceCallsign = "";
var runningApp = undefined;

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
    apiPassword = getParameterByName('apiPassword');
    if (apiPassword != null)
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
    $.ajax({
        url: "jaxt/api/settings/?apiPassword=" + apiPassword,
        type: 'GET',
        dataType: 'json',
        success: (data) => {
            
            if (data.hasOwnProperty('source'))
            {
                sourceCallsign = data.source;
                if (sourceCallsign == "")
                    sourceCallsign = null;
            }
            setupWebsocket();
        },
        error: () => { continueStartup(); }
    });
}

window.onresize = function() {
    fitStuff();
}

function prompt(term)
{
    command = '';
    term.write('\r\n$ ');
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
    exit: {
        f: (args) => {
            window.close();
        },
        description: 'Exit this terminal'
    },
  };

const remoteApp = {
    start: () => {

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
    if ((runningApp != null && runningApp != undefined) && packet.destination == sourceCallsign)
    {
        //console.log("App should handle packet!");
        runningApp.handlePacket(packet);
    }
}

function runFakeTerminal() 
{
    if (term._initialized) {
      return;
    }
    term.write('Welcome to the JAXT terminal, type "help" for a list of commands.\r\n');
    if (sourceCallsign != "")
        term.write('Your callsign is set to \"' + sourceCallsign + '\"\r\n');
    else
        term.write('Your callsign is NOT set!\r\n');
    term._initialized = true;
    term.prompt = () => {
        term.write('\r\n$ ');
    };
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
            if (term._core.buffer.x > 2) {
              term.write('\b \b');
              if (command.length > 0) {
                command = command.substr(0, command.length - 1);
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
        "apiPassword": apiPassword,
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
                    runFakeTerminal();
                } else if (action == 'authFail') {
                    document.getElementById('errorMsg').innerHTML = jsonObject.error;
                } else if (action == 'kissConnected') {
                    term.writeln("WARNING: KISS Connected");
                } else if (action == 'kissDisconnected') {
                    term.writeln("WARNING: Disconnected");
                } else if (action == 'write') {
                    term.write(jsonObject.data);
                } else if (action == 'commands') {
                    Object.entries(jsonObject.commands).forEach((entry) => {
                        const [key, value] = entry;
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