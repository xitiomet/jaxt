## JAXT - An AX.25 Tool

A AX.25 Command line tool, Web Interface and Java library

This program was created because i wanted a reliable way to interface modern web programs using Websockets,
JSON, and HTTP with AX.25 packet radio over KISS. This program can translate AX.25 frames to JSON objects and back again without any information lost. It can be configured to perform an HTTP POST on the receipt of a packet, and transmission can be initiated from an HTTP api call.

![](https://openstatic.org/projects/jaxt/jaxt-web.png)

This program has been tested with [UZ7HO's Soundmodem](https://uz7.ho.ua/packetradio.htm), [QtSoundmodem](https://www.cantab.net/users/john.wiseman/Documents/QtSoundModem.html) and [DireWolf](https://github.com/wb2osz/direwolf)

NOTE: Connect to the KISS port and IP of your TNC. Does not work with AGWPE

```bash
usage: jaxt
Java AX25 Tool: A Java KISS TNC Client implementation
 -?,--help                  Shows help
 -a,--api <arg>             Enable API Web Server, and specify port
                            (Default: 8101)
 -c,--commads <arg>         Specify commands.json file location for web
                            terminal
 -d,--destination <arg>     Destination callsign (for test payload)
 -f,--config-file <arg>     Specify config file (.json)
 -h,--host <arg>            Specify TNC host (Default: 127.0.0.1)
 -l,--logs <arg>            Enable Logging, and optionally specify a
                            directory
 -m,--payload <arg>         Payload string to send on test interval.
                            {{ts}} for timestamp, {{seq}} for sequence.
 -p,--port <arg>            KISS Port (Default: 8100)
 -s,--source <arg>          Set the default source callsign.
 -t,--test <arg>            Send test packets (optional parameter interval
                            in seconds, default is 10 seconds)
 -v,--verbose               Shows Packets
 -x,--post <arg>            HTTP POST packets received as JSON to url
 -z,--terminal-link <arg>   Listen for a terminal call, first argument is
                            callsign, and second is command with
                            parameters seperated by commas (Example: -z
                            MYCALL-1 cmd.exe,/Q)
 ```

 If you wish to avoid a lot of command line arguments, the config file format looks like this save as .json:

 ```json
{
    "host": "127.0.0.1",
    "port": 8100,
    "verbose": true,
    "source": "NOCALL-1",
    "destination": "NOCALL-2",
    "payload": "I am sending message #{{seq}} at {{ts}}",
    "txTest": false,
    "txTestInterval": 10000,
    "postUrl": "https://mywebsite.com/payloadhandler",
    "logPath": "./jaxt-logs/",
    "apiPort": 8101,
    "apiPassword": "locked-down-password1234",
    "txDisabled": false,
    "terminal": {
        "CMD-1": {
            "type": "process",
            "execute": [
                "cmd.exe",
                "/Q"
            ]
        },
        "BASH-1": {
            "type": "process",
            "execute": [
                "/bin/bash",
                "--login"
            ]
        }
    }
}
 ```
 If you would like automatic persistent settings, save your config file as ".jaxt.json" in your home directory. JAXT will look for this file when no config file is specified. Or use the command line option "-f ~/.jaxt.json" to create this file automatically with the settings from the command line.

 ### Logging

In the "logPath" (which defaults to the current directory) you will find "main.log", "exceptions.log", "rx.json" and "tx.json"

* "main.log" - this log contains connection information, and status information.
* "exceptions.log" - this log contains advanced debugging info for jaxt.
* "rx.json" - A Log of all packets received using jaxt, each line represents one json object. Check out [JSON-Roller](https://openstatic.org/projects/json-roller/) for help parsing and filtering this information.
* "tx.json" - A Log of all packets transmitted using jaxt, each line represents one json object. Check out [JSON-Roller](https://openstatic.org/projects/json-roller/) for help parsing and filtering this information.

### Posting packets

If you would like received packets to be posted to a webserver, you can use the -x option with a valid http/https url.

The format of the post will be:

Content-Type: application/json

```json
{
    "source": "NOCALL-1",
    "destination": "NOCALL-2",
    "timestamp": 1686600213326,
    "payload": "This is a transmission",
    "control": ["UI","C"],
    "protocol": 240,
    "direction": "rx",
    "path": ["WIDE1-1"]
}
```
* "source" - source callsign of packet with SSID specified as -1
* "destination" - destination callsign of packet with SSID specified as -1
* "timestamp" - timestamp at which JAXT received the packet
* "payload" - body of the packet as a string
* "control" - control field as an array of control states. (UI, SABM, DISC, I, REJ, RR, C or R, P or F, R0, S0)
    * This field usually has the packet type as the first element of the array however the order doesn't matter. Refer to [AX.25 Documentation](https://openstatic.org/projects/jaxt/AX25.2.2.pdf) for more information.
    * "C" or "R" - Command or response
    * "P" or "F" - Poll or final
    * "R0" - Receive Modulo 0-7
    * "S0" - Send Modulo 0-7
* "protocol" - protocol field as an unsigned integer
* "direction" - "rx" or "tx" did JAXT receive or transmit this packet?
* "path" - callsign path for digipeters.

### Websocket API

if you use the -a option you can communicate with jaxt using Websockets or the HTTP interface (shown above, and explained below this section)

Assuming the api port is set to 8101, You can connect to the server's websocket using:

```bash
wscat -c ws://127.0.0.1:8101/jaxt/
```

Once connected you must transmit an object containing {"apiPassword":"xxxxxxx"} before you will receive any packets or be able to transmit. This can even be included as a field in your first packet. 

The format of AX.25 packets is the same as the POST packet format documented above. JSON objects should be sent as a single line without any CR/LF.

### HTTP API paths

You can view and send packets using the simple HTTP interface at:

http://127.0.0.1:8101/

NOTE: If you plan to make your JAXT instance internet available, its recommended you use nginx to provide ssl protection on both the websocket and http interface. Otherwise your password will be transmitted in plain text!


* /jaxt/api/transmit/ - You can either POST a packet as a JSON object to this path, or by specifying "source", "destination", and "payload" parameters using GET. Both must include "apiPassword" field as well.

* /jaxt/api/settings/ - Retrieve current JAXT settings. Must include "apiPassword" which is excluded from the response for security reasons.

### SABM Terminal Server (host your own BBS)

JAXT supports inbound SABM connections using programs like EasyTerm. You can specify any program 
to be launched upon connection and the program's STDIN, STDOUT, and STDERR will be connected to 
the remote host.

For example if you wanted to make a bash terminal available (not a good idea)
```bash
$ jaxt -z MYCALL-4 /bin/bash
```

Lets say you made a python script for a BBS instead
```bash
$ jaxt -z MYCALL-4 /usr/bin/python,/home/me/myscript.py
```
NOTE: commas are used to seperate parameters after the command instead of spaces (as they would be treated as arguments for jaxt)

### Web Terminal

![](https://openstatic.org/projects/jaxt/jaxt-simple-wt.png)

For remote control of JAXT, there is a web terminal in the top bar of the web interface.

![](https://openstatic.org/projects/jaxt/jaxt-wt.png)

This web terminal is pretty simple, but can be extended with your own commands. It also provides
a terminal client to your TNC, this will allow you to connect to remote radio BBS's and other services.

$ connect bbs-1

(this will start sending out SABM messages to bbs-1 waiting for a UA response, once the responce is received a connection is formed)

### Web Terminal Commands

To allow customization of the web terminal you can add your own commands using a commands.json file (specified using -c)
Each entry's key should be the command the user will type into the terminal, "description" is used for the "help" command,
and "execute" is what should be run at the OS level. Be careful with this feature, security can be jeapordized!

"ignoreExtraArgs" can be used to make sure nothing extra can be passed to the os level command.

```json
{
  "reclist":{
    "execute": ["arecord","-l"],
    "description": "List Recording devices",
    "ignoreExtraArgs": true
  },
  "lsusb":{
    "execute": ["lsusb"],
    "description": "List USB devices",
    "ignoreExtraArgs": true
  },
  "pidof":{
    "execute": ["pidof"],
    "description": "Get pid of running process"
  }
}
```