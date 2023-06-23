function readRxMod(packet)
{
  const values = ['R0', 'R1', 'R2', 'R3', 'R4', 'R5', 'R6', 'R7'];
  const packetControl = packet.control;
  for (const itm of packetControl)
  {
    const idx = values.indexOf(itm);
    if (idx !== -1) {
        return idx;
    }
  }
  return -1;
}

function readTxMod(packet)
{
  const values = ['S0', 'S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'S7'];
  const packetControl = packet.control;
  for (const itm of packetControl)
  {
    const idx = values.indexOf(itm);
    if (idx !== -1) {
        return idx;
    }
  }
  return -1;
}

function modIncreased(modValue)
{
    var rv = modValue + 1;
    if (rv >= 8) rv = 0;
    return rv;
}

const connectApp = {
    sabmComplete: false,
    destCallsign: null,
    txMod: 0,
    rxMod: 0,
    myInterval: null,
    commandOnDeck: null,
    remoteReceiveNotReady: false,
    everyTwoSeconds: () => {
        if (!connectApp.sabmComplete)
        {
            sendEvent({
                "source": sourceCallsign,
                "destination": connectApp.destCallsign,
                "control": ["SABM","C","P"]
            });
        } else if (connectApp.commandOnDeck != null && !connectApp.remoteReceiveNotReady) {
            sendEvent({
                "source": sourceCallsign,
                "destination": connectApp.destCallsign,
                "control": ["I","C","P", "R" + connectApp.rxMod, "S" + connectApp.txMod],
                "payload": connectApp.commandOnDeck + "\r"
            });
        }
    },
    start: (args) => {
        connectApp.sabmComplete = false;
        connectApp.destCallsign = args[0].toUpperCase();
        connectApp.commandOnDeck = null;
        connectApp.rxMod = 0;
        connectApp.txMod = 0;
        connectApp.remoteReceiveNotReady = true;
        term.write("Connecting to " + connectApp.destCallsign + "....");
        connectApp.myInterval = setInterval(connectApp.everyTwoSeconds,2000);
    },
    handleCommand: (command) => {
        connectApp.commandOnDeck = command;
        term.writeln('');
    },
    handlePacket: (packet) => {
        //term.writeln(JSON.stringify(packet));
        if (!connectApp.sabmComplete)
        {
            if (packet.control.includes('UA') || packet.control.includes('I'))
            {
                term.writeln("CONNECTED!");
                connectApp.sabmComplete = true;
                connectApp.remoteReceiveNotReady = false;
            }
        } 
        if (packet.control.includes('I')) 
        {
            var packetRxMod = readTxMod(packet);
            if (packetRxMod == connectApp.rxMod)
            {
                term.write(packet.payload);
                connectApp.rxMod = modIncreased(packetRxMod);
            }
            sendEvent({
                "source": sourceCallsign,
                "destination": connectApp.destCallsign,
                "control": ["RR","R","R" + connectApp.rxMod]
            });
        }
        if (packet.control.includes('RR')) 
        {
            if (packet.control.includes('R'))
            {
                connectApp.txMod = readRxMod(packet);
                if (connectApp.txMod >= 8) connectApp.txMod = 0;
                connectApp.commandOnDeck = null;
                connectApp.remoteReceiveNotReady = false;
            }
        }
        if (packet.control.include('DISC'))
        {
            if (connectApp.myInterval != null)
                clearInterval(connectApp.myInterval);
            connectApp.myInterval = null;
            runningApp = null;
            prompt(term);
        }
    },
    stop: () => {
        if (connectApp.myInterval != null)
            clearInterval(connectApp.myInterval);
        sendEvent({
            "source": sourceCallsign,
            "destination": connectApp.destCallsign,
            "control": ["DISC","C"]
        });
        connectApp.myInterval = null;
    }
}