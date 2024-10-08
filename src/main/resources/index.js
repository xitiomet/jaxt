var connection;
var term;
var fitAddon = new FitAddon.FitAddon();
var command = '';
var debugMode = false;
var reconnectTimeout;
var hostname = location.hostname;
var protocol = location.protocol;
var port = location.port;
var wsProtocol = 'ws';
var httpUrl = '';
var termAuth = '';
var playingDevice = -1;
var playingStream = null;
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

function padString(str, len) {
  if (str.length < len) {
    return str.padEnd(len, ' ');
  } else if (str.length > len) {
    return str.slice(0, len);
  } else {
    return str;
  }
}

function switchTo(mainScreenId)
{
    var mainScreens = document.getElementsByClassName('mainScreen');
    for(const scrn of mainScreens)
    {
        scrn.style.display = 'none';
    }
    var mainScreen = document.getElementById(mainScreenId);
    document.body.style.backgroundColor = mainScreen.style.backgroundColor;
    mainScreen.style.display = 'block';
    if (mainScreenId == 'terminalScreen')
    {
        document.getElementById('topBar').style.backgroundColor = '#222222';
        document.getElementById('consoleButton').style.display = 'inline-block';
        document.getElementById('terminalButton').style.display = 'none';
        setTimeout(() => {
            console.log("Fit Stuff!");
            fitStuff();
        },1000);
    } else {
        document.getElementById('topBar').style.backgroundColor = '#000000';
        document.getElementById('consoleButton').style.display = 'none';
        document.getElementById('terminalButton').style.display = 'inline-block';
    }
}

function switchLED(color)
{
    if (color == 'red')
    {
        document.getElementById('redLed').style.display = 'inline-block';
    } else {
        document.getElementById('redLed').style.display = 'none';
    }
    if (color == 'green')
    {
        document.getElementById('greenLed').style.display = 'inline-block';
    } else {
        document.getElementById('greenLed').style.display = 'none';
    }
    if (color == 'grey')
    {
        document.getElementById('greyLed').style.display = 'inline-block';
    } else {
        document.getElementById('greyLed').style.display = 'none';
    }
}

function updateKCS(v)
{
    if (v)
    {
        switchLED('green');
        //logIt("KISS Server Connected");
    } else {
        switchLED('red');
        //logIt("KISS Server Disconnected");
    }
}

function popupWindow(url, windowName, w, h) {
    const y = window.top.outerHeight / 2 + window.top.screenY - ( h / 2);
    const x = window.top.outerWidth / 2 + window.top.screenX - ( w / 2);
    return window.open(url, windowName, `toolbar=no, location=no, directories=no, status=no, menubar=no, scrollbars=no, resizable=no, copyhistory=no, width=${w}, height=${h}, top=${y}, left=${x}`);
}

function doTxWindow()
{
    var myWindow = popupWindow('tx.html?termAuth=' + encodeURIComponent(termAuth), "Transmit", 455, 426);
}

function fitStuff()
{
    var terminalElement = document.getElementById('terminal');
    if (terminalElement != undefined)
    {
        terminalElement.style.height = (window.innerHeight - 60) + 'px';
        terminalElement.style.width = window.innerWidth - 10 + 'px';
        fitAddon.fit();
    }
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
    lsradio: {
        f: (args) => {
          sendEvent({"action":"lsradio"});
        },
        description: 'List Radio devcies',
    },
    lucs: {
        f: (args) => {
          sendEvent({"action":"lucs", "callsign": args[0]});
        },
        description: 'Look up callsign information',
    },
    stopradio: {
        f: (args) => {
          sendEvent({"action":"stopradio", "devId": parseInt(args[0])});
          prompt(term);
        },
        description: 'Stop an audio device (use lsradio for dev#)',
    },
    startradio: {
        f: (args) => {
          sendEvent({"action":"startradio", "devId": parseInt(args[0])});
          prompt(term);
        },
        description: 'Start an audio device (use lsradio for dev#)',
    },
    monitor: {
        f: (args) => {
          sendEvent({"action":"monitorradio", "sourceDevId": parseInt(args[0]) , "destDevId": parseInt(args[1])});
          prompt(term);
        },
        description: 'Monitor an audio device from another audio device (use lsradio for dev#)\r\nExample: monitor src# dest#',
    },
    unmonitor: {
        f: (args) => {
          sendEvent({"action":"unmonitorradio", "sourceDevId": parseInt(args[0]) , "destDevId": parseInt(args[1])});
          prompt(term);
        },
        description: 'UN-Monitor an audio device from another audio device (use lsradio for dev#)\r\nExample: monitor src# dest#',
    },
    setradio: {
        f: (args) => {
          var event = {"action":"setradio", "devId": parseInt(args[0])};
          if (args.length >= 3)
          {
            var key = args[1];
            event['key'] = key;
            var v = args.slice(2).join(' ');
            if (v == 'true')
            {
                v = true;
            } else if (v == 'false') {
                v = false;
            } else if (v.includes(',')) {
                v = v.split(',');
                if (key == 'ptt')
                {
                    var pttType = v[0];
                    var second = v[1];
                    if (pttType == "rts" || pttType == "dtr")
                        v = {"type": pttType, "serialPort": second};
                    else
                        v = {"type": "none"};
                }
            }
            event['value'] = v;
          }
          sendEvent(event);
        },
        description: 'Change or retrieve settings for an radio device\r\n(use lsradio for dev#)',
    },
    unsetradio: {
        f: (args) => {
          var event = {"action":"unsetradio", "devId": parseInt(args[0])};
          if (args.length >= 2)
          {
            var key = args[1];
            event['key'] = key;
          }
          sendEvent(event);
        },
        description: 'unset a parameter for an audio device\r\n(use lsradio for dev#)',
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
            term.writeln("Listening to device #" + args[0] + " use \"mute\" to stop!");
            listenClick(args[0]);
            prompt(term);
        },
        description: 'Listen to an audio device via the current web browser\r\nExample: "listen 2" (use lsradio to get device number)'
    },
    mute: {
        f: (args) => {
            term.writeln("Stopping Audio!");
            listenClick(-1);
            var audioClipElement = document.getElementById('audioClipElement');
            audioClipElement.src = '';
            prompt(term);
        },
        description: 'Stop any audio playing'
    },
    clear: {
        f: (args) => {
            setTimeout(() => {
                term.clear();
                prompt(term);
            },1000)
        },
        description: 'Clear Terminal Screen'
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
                        var finishCmd = cmd.substr(command.length) + ' ';
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

function clearHistory()
{
    if (confirm("Clear Packet History?"))
    {
        sendEvent({
            "action": "clearHistory"
        });
        var console = document.getElementById('console');
        console.innerHTML = '';
    }
}

function doAuth()
{
    sendEvent({
        "apiPassword": document.getElementById('password').value,
        "termId": Date.now()
    });
}

function playAudio(uri)
{
    var audioClipElement = document.getElementById('audioClipElement');
    audioClipElement.src = uri;
    audioClipElement.play();
}

function listenClick(devId)
{
    var sdd = document.getElementById('selectDeviceDiv');
    var bodyTag = document.getElementById('bodyTag');
    if (sdd != null)
        bodyTag.removeChild(sdd);
    var audioElement = document.getElementById('audioElement');
    if (devId >= 0)
    {
        var streamUrl = "jaxt/api/stream/?termAuth=" + termAuth + "&devId=" + devId;
        console.log("Opening Stream: " + streamUrl);
        audioElement.src = streamUrl;
        audioElement.play();
        playingDevice = devId;
        playingStream = streamUrl;
    } else {
        console.log("Closing Stream: " + audioElement.src);
        audioElement.src = "";
        document.getElementById('speakerButton').style.backgroundColor = 'black';
        playingDevice = -1;
        playingStream = null;
    }
}

function listen()
{
    $.ajax({
        url: "jaxt/api/audio/?termAuth=" + termAuth,
        type: 'GET',
        dataType: 'json',
        success: (data) => {
            if (data.hasOwnProperty('devices'))
            {
                if (data.devices.length > 0)
                {
                    var audioElement = document.getElementById('audioElement');
                    var selectDeviceDiv = document.createElement("div");
                    selectDeviceDiv.className = "modal";
                    selectDeviceDiv.id = "selectDeviceDiv";
                    var i = 0;
                    for(const recDev of data.devices)
                    {
                        var devState = data.state[recDev];
                        if (devState.canBeRecorded == true)
                        {
                            var devDiv = document.createElement("div");
                            devDiv.style.width = '100%';
                            devDiv.style.height = '54px';
                            devDiv.style.display = 'flex';
                            devDiv.style.border = '1px dotted black';
                            devDiv.style.padding = '0px 0px 0px 0px';
                            devDiv.style.margin = '0px 0px 0px 0px';


                            var textDiv = document.createElement("div");
                            textDiv.style.width = '100%';
                            textDiv.style.height = '54px';
                            textDiv.style.display = 'flex';
                            textDiv.style.border = 'none';
                            textDiv.style.padding = '0px 0px 0px 0px';
                            textDiv.style.margin = '0px 0px 0px 0px';
                            textDiv.style.display = 'inline-block';
                            textDiv.style.verticalAlign = 'middle';
                            textDiv.style.textAlign = 'center';
                            textDiv.style.color = 'black';
                            var extraText = '';
                            if (devState.settings.hasOwnProperty('frequency'))
                            {
                                extraText += "<i>Frequency " + devState.settings.frequency + "hz</i>";
                            }
                            var devTitle = "<b style=\"font-size: 18px;\">" + recDev + "</b>";
                            textDiv.innerHTML = "<div style=\"padding-top: 6px;\">" + devTitle + "<br />" + extraText + "</div>";

                            var listenButton = document.createElement("button");
                            listenButton.id = "audioDev_" + i;
                            listenButton.onclick = function() {
                                var devId = parseInt(this.id.substr(9));
                                //alert(devId);
                                listenClick(devId);
                            };
                            listenButton.style.width = '62px';
                            listenButton.style.height = '54px';
                            listenButton.style.backgroundPosition = 'center';
                            listenButton.style.backgroundRepeat = 'no-repeat';
                            listenButton.style.backgroundImage = "url('speaker-48.png')";
                            if (playingDevice == i)
                            {
                                listenButton.style.backgroundColor = "lightgreen";
                                textDiv.style.backgroundColor = "lightgreen";
                            } else if (!devState.isAlive) {
                                textDiv.style.backgroundColor = "lightgray";

                            }

                            var stopButton = document.createElement("button");
                            stopButton.id = "astopDev_" + i;
                            if (devState.isAlive)
                            {
                                stopButton.title = 'Stop Device';
                                stopButton.onclick = function() {
                                    var devId = parseInt(this.id.substr(9));
                                    //alert(devId);
                                    sendEvent({"action":"stopradio", "devId": devId});
                                    var sdd = document.getElementById('selectDeviceDiv');
                                    var bodyTag = document.getElementById('bodyTag');
                                    if (sdd != null)
                                        bodyTag.removeChild(sdd);
                                };
                                stopButton.style.backgroundImage = "url('quit.png')";
                            } else {
                                stopButton.title = 'Start Device';
                                stopButton.onclick = function() {
                                    var devId = parseInt(this.id.substr(9));
                                    //alert(devId);
                                    sendEvent({"action":"startradio", "devId": devId});
                                    var sdd = document.getElementById('selectDeviceDiv');
                                    var bodyTag = document.getElementById('bodyTag');
                                    if (sdd != null)
                                        bodyTag.removeChild(sdd);
                                };
                                stopButton.style.backgroundImage = "url('start.png')";
                            }
                            stopButton.style.backgroundPosition = 'center';
                            stopButton.style.backgroundRepeat = 'no-repeat';
                            stopButton.style.width = '62px';
                            stopButton.style.height = '54px';

                            devDiv.appendChild(textDiv);
                            devDiv.appendChild(listenButton);
                            devDiv.appendChild(stopButton);
                            selectDeviceDiv.appendChild(devDiv);
                        }
                        i++;
                    }
                    var muteButton = document.createElement("button");
                    muteButton.onclick = () => { listenClick(-1); }
                    muteButton.style.width = '100%';
                    muteButton.style.height = '54px';
                    muteButton.style.backgroundColor = 'red';
                    muteButton.style.fontSize = '18px';
                    muteButton.innerText = "MUTE";
                    selectDeviceDiv.appendChild(muteButton);

                    var cancelButton = document.createElement("button");
                    cancelButton.onclick = () => { 
                        var sdd = document.getElementById('selectDeviceDiv');
                        var bodyTag = document.getElementById('bodyTag');
                        if (sdd != null)
                            bodyTag.removeChild(sdd);
                     }
                    cancelButton.style.width = '100%';
                    cancelButton.style.height = '54px';
                    cancelButton.style.fontSize = '18px';
                    cancelButton.innerText = "CANCEL";
                    selectDeviceDiv.appendChild(cancelButton);
                    document.getElementById('bodyTag').appendChild(selectDeviceDiv);
                } else {
                    alert("No Audio Devices found!");
                }
            }
        },
        error: () => {}
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
            if (document.getElementById('login').style.display == 'none')
            {
                doAuth();
            }
        };
        
        connection.onerror = function (error) {
            //document.getElementById('connectionLed').src="led-grey.svg";
        };

        //Code for handling incoming Websocket messages from the server
        connection.onmessage = function (e) {
            if (debugMode)
            {
                console.log("Receive: " + e.data);
            }
            var jsonObject = JSON.parse(e.data);
            if (jsonObject.hasOwnProperty("hostname"))
            {
                document.getElementById('hostname').innerHTML = jsonObject.hostname;
            }
            if (jsonObject.hasOwnProperty("action"))
            {
                var action = jsonObject.action;
                if (action == 'authOk') {
                    sourceCallsign = jsonObject.source;
                    if (sourceCallsign == null)
                        sourceCallsign = "";
                    jaxtHostname = jsonObject.hostname;
                    document.getElementById('login').style.display = 'none';
                    document.getElementById('console').style.display = 'block';
                    document.getElementById('clearButton').style.display = 'inline-block';
                    document.getElementById('speakerButton').style.display = 'inline-block';
                    if (!jsonObject.txDisabled)
                    {
                        document.getElementById('txButton').style.display = 'inline-block';
                    }
                    document.getElementById('terminalButton').style.display = 'inline-block';
                    termAuth = jsonObject.termAuth;
                    updateKCS(jsonObject.kissConnected)
                    sendEvent({
                        "history": 100
                    });
                    runFakeTerminal();
                } else if (action == 'authFail') {
                    document.getElementById('errorMsg').innerHTML = jsonObject.error;
                } else if (action == 'recording') {
                    logRecording(jsonObject);
                } else if (action == 'kissConnected') {
                    updateKCS(true);
                } else if (action == 'kissDisconnected') {
                    updateKCS(false);
                } else if (action == 'info') {
                    logInfo(jsonObject);
                } else if (action == 'APRS') {
                    logAPRS(jsonObject);
                } else if (action == 'dtmfSequence') {
                    logDTMFSequence(jsonObject);
                } else if (action == 'startradio') {
                    if (playingDevice == jsonObject.devId)
                    {
                        var audioElement = document.getElementById('audioElement');
                        audioElement.src = playingStream;
                        audioElement.load();
                        audioElement.play(); 
                    }
                } else if (action == 'stopradio') {
                    if (playingDevice == jsonObject.devId)
                    {
                        var audioElement = document.getElementById('audioElement');
                        audioElement.pause();
                    }
                } else if (action == 'lsradio') {
                    var a = 0;
                    term.writeln("-- Radio Devices --");
                    for(devname of jsonObject.devices)
                    {
                        if (jsonObject.state.hasOwnProperty(devname))
                        {
                            term.write(a + ": " + devname);
                            var stateObj = jsonObject.state[devname];
                            if (stateObj.canBeRecorded == true && stateObj.canPlayTo == true)
                            {
                               term.write(" \x1B[0;96m(IN/OUT)\x1B[0m");
                            } else if (stateObj.canBeRecorded == false && stateObj.canPlayTo == true) {
                               term.write(" \x1B[0;96m(OUT)\x1B[0m");
                            } else if (stateObj.canBeRecorded == true && stateObj.canPlayTo == false) {
                                term.write(" \x1B[0;96m(IN)\x1B[0m");
                            }
                            if (stateObj.isAlive == true)
                            {
                               term.write(" \x1B[0;92m(Active)\x1B[0m");
                            }
                            if (stateObj['settings']['autoRecord'] == true)
                            {
                                term.write(" \x1B[0;91m(A-REC)\x1B[0m");
                            }
                            term.writeln("");
                            for(tarname of stateObj.targets)
                            {
                                term.writeln(" -> " + jsonObject.devices.indexOf(tarname) + ": " + tarname);
                            }
                            a++;
                        }
                    }
                    prompt(term);
                } else if (action == 'setradio') {
                    var a = 0;
                    term.writeln("-- " + jsonObject.devId + ": " + jsonObject.name + " --");
                    for(let key in jsonObject.mixerSettings)
                    {
                        let value = jsonObject.mixerSettings[key];
                        if (value instanceof Object)
                        {
                            term.writeln(key + ": " + JSON.stringify(value));
                        } else {
                            term.writeln(key + ": " + value);
                        }
                    }
                    prompt(term);
                } else if (action == 'lucs') {
                    var callsign = jsonObject.callsign;
                    if (callsign.hasOwnProperty('error'))
                    {
                        term.writeln("\x1B[0;91mERROR: " + callsign['error'] + "\x1B[0m");
                    } else {
                        term.writeln("--- CALLSIGN " + callsign['call'] + " ---");
                        term.writeln((callsign['fname'] + ' ' + callsign['name']).trim());
                        if (callsign['class'] == "T")
                            term.writeln("\x1B[0;91mTechnician\x1B[0m");
                        else if (callsign['class'] == "G")
                            term.writeln("\x1B[0;93mGeneral\x1B[0m");
                        else if (callsign['class'] == "E")
                            term.writeln("\x1B[0;92mAmateur Extra\x1B[0m");
                        term.writeln(callsign['addr1']);
                        term.writeln(callsign['addr2'] + ', ' + callsign['state'] + ' ' + callsign['zip']);
                        term.writeln("Expires: " + callsign['expires']);
                        term.writeln("Status: " + callsign['status']);
                        term.writeln("Location: " + callsign['lat'] + ", " + callsign['lon'] + " " + callsign['grid']);
                    }
                    prompt(term);
                } else if (action == 'unsetradio') {
                    var a = 0;
                    term.writeln("-- " + jsonObject.devId + ": " + jsonObject.name + " --");
                    for(let key in jsonObject.mixerSettings)
                    {
                        let value = jsonObject.mixerSettings[key];
                        if (value instanceof Object)
                        {
                            term.writeln(key + ": " + JSON.stringify(value));
                        } else {
                            term.writeln(key + ": " + value);
                        }
                    }
                    prompt(term);
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
                logPacket(jsonObject);
                handlePacket(jsonObject);
            }
        };
        
        connection.onclose = function () 
        {
            switchLED('grey');
            console.log('WebSocket connection closed');
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

function logIt(message, color = '#BBBBBB')
{
    var console = document.getElementById('console');
    var d = new Date();
    var msgSplit = message.split(/\r?\n/);
    for (var i = 0; i < msgSplit.length; i++)
    {
        console.innerHTML +=  "<pre style=\"color: " + color + ";\">(INFO " + getDTString(d) + ") " + msgSplit[i] + "</pre>";
    }
    window.scrollTo(0,document.body.scrollHeight);
}

String.prototype.hashCode = function() {
    var hash = 0,
      i, chr;
    if (this.length === 0) return hash;
    for (i = 0; i < this.length; i++) {
      chr = this.charCodeAt(i);
      hash = ((hash << 5) - hash) + chr;
      hash |= 0; // Convert to 32bit integer
    }
    return hash;
  }

function logInfo(jsonObject, color = '#BBBBBB')
{
    var message = jsonObject.text;
    var console = document.getElementById('console');
    var d = new Date();
    if (jsonObject.hasOwnProperty('timestamp'))
        d = new Date(jsonObject.timestamp);
    var preId = "info_" + d.getTime() + message.hashCode();
    if (document.getElementById(preId) == undefined)
    {
        var msgSplit = message.split(/\r?\n/);
        for (var i = 0; i < msgSplit.length; i++)
        {
            console.innerHTML +=  "<pre id=\"" + preId + "\" style=\"color: " + color + ";\">(INFO " + getDTString(d) + ") " + msgSplit[i] + "</pre>";
        }
        window.scrollTo(0,document.body.scrollHeight);
    }
}

function cleanPayload(payload)
{
    if (payload != undefined)
        return payload.replaceAll(/</g,'&lt;').replaceAll(/>/g,'&gt;').replaceAll(/\r/g,'&lt;CR&gt;').replaceAll(/\n/g,'&lt;LF&gt;');
    else
        return "";
}

function logPacket(packet)
{
    var console = document.getElementById('console');
    var d = new Date();
    if (packet.hasOwnProperty('timestamp'))
        d = new Date(packet.timestamp);
    var preId = packet.source + "_" + packet.destination + "_" + d.getTime();
    if (document.getElementById(preId) == undefined)
    {
        var ctrlStr = "";
        for(flag of packet.control)
        {
            ctrlStr += " " + flag;
        }
        console.innerHTML +=  "<pre id=\"" + preId + "\">(" + packet.direction.toUpperCase() + " @ " + getDTString(d) + ") " + padString(packet.source,9) + " > " + padString(packet.destination,9) + " [" + padString(ctrlStr,14) + " ] " + cleanPayload(packet.payload) + "</pre>";
        window.scrollTo(0,document.body.scrollHeight);
    }
}

function logRecording(jsonObject)
{
    var console = document.getElementById('console');
    var d = new Date();
    if (jsonObject.hasOwnProperty('timestamp'))
        d = new Date(jsonObject.timestamp);
    var divId = jsonObject.uri + "_" + d.getTime();
    if (document.getElementById(divId) == undefined)
    {
        var line =  "<div id=\"" + divId + "\" style=\"color: #FF5733;\">( REC " + getDTString(d) + ") " + jsonObject.device + ": " + jsonObject.name + " <a href=\"" + jsonObject.uri + "\" target=\"_blank\">(Download)</a> <a href=\"#\" onclick=\"playAudio('" + jsonObject.uri + "');event.preventDefault();\">(Listen)</a> " + jsonObject.duration + "ms";
        line += "</div>";
        console.innerHTML += line;
        window.scrollTo(0,document.body.scrollHeight);
    }
}

function logDTMFSequence(jsonObject)
{
    var console = document.getElementById('console');
    var d = new Date();
    if (jsonObject.hasOwnProperty('timestamp'))
        d = new Date(jsonObject.timestamp);
    var divId = jsonObject.sequence + "_" + d.getTime();
    if (document.getElementById(divId) == undefined)
    {
        var line =  "<div id=\"" + divId + "\" style=\"color: #1cb4d6;\">(DTMF " + getDTString(d) + ") " + jsonObject.device + ": " + jsonObject.sequence + "</div>";
        console.innerHTML += line;
        window.scrollTo(0,document.body.scrollHeight);
    }
}

function removeSSIDFromCallsign(callsign) {
    // Regex to match the SSID pattern in a callsign
    const ssidPattern = /-\d{1,2}$/;
    
    // Replace the matched SSID with an empty string
    return callsign.replace(ssidPattern, '');
  }
  

function logAPRS(jsonObject)
{
    var console = document.getElementById('console');
    var d = new Date();
    if (jsonObject.hasOwnProperty('timestamp'))
        d = new Date(jsonObject.timestamp);
    var divId = jsonObject.source + "_" + jsonObject.type + "_" + d.getTime();
    if (document.getElementById(divId) == undefined)
    {
        var line =  "<div id=\"" + divId + "\" style=\"color: #1cb4d6;\">(APRS " + getDTString(d) + ") " + jsonObject.type + ": <a target=\"_blank\" href=\"https://www.qrz.com/db/" + removeSSIDFromCallsign(jsonObject.source) + "\">" + jsonObject.source + "</a> <a target=\"_blank\" href=\"https://aprs.fi/#!call=a%2F" + jsonObject.source + "&timerange=3600&tail=3600\">aprs.fi</a>";
        if (jsonObject.hasOwnProperty('latitude') && jsonObject.hasOwnProperty('longitude'))
        {
            var lat = jsonObject.latitude.toFixed(6);
            var lon = jsonObject.longitude.toFixed(6);
            line += " <a target=\"_blank\" href=\"https://www.google.com/maps/?q=" + lat + "," + lon + "\">" + lat + "," + lon + "</a>"
        }
        if (jsonObject.hasOwnProperty('sourceCallsign'))
        {
            var sourceCallsign = jsonObject.sourceCallsign;
            if (sourceCallsign.hasOwnProperty('fname') && sourceCallsign.hasOwnProperty('name') && sourceCallsign.hasOwnProperty('addr2') && sourceCallsign.hasOwnProperty('state'))
            {
                line += " (" + (sourceCallsign.fname + " " + sourceCallsign.name).trim() + " - " + sourceCallsign.addr2.trim() + ", " + sourceCallsign.state + ")";
            }
        }
        line += " " + jsonObject.comment + "</div>";
        console.innerHTML += line;
        window.scrollTo(0,document.body.scrollHeight);
    }
}

window.onload = function() {
    var audioElement = document.getElementById('audioElement');
    audioElement.onplay = function() {
        document.getElementById('speakerButton').style.backgroundColor = 'red';
    };
    audioElement.onclose = function() {
        document.getElementById('speakerButton').style.backgroundColor = 'black';
    };
    audioElement.onerror = function() {
        document.getElementById('speakerButton').style.backgroundColor = 'black';
        if (playingStream != null)
        {
            audioElement.src = playingStream;
            audioElement.play();
        }
    };
    audioElement.onended = function() {
        document.getElementById('speakerButton').style.backgroundColor = 'black';
    };
    term = new Terminal({cursorBlink: true, allowProposedApi: false});
    term.open(document.getElementById('terminal')); 
    term.loadAddon(fitAddon);
    fitStuff();
    setupWebsocket();
};

