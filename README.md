## Java AX.25 Tool

A Java AX.25 Command line tool and library

To compile this project please run:
```bash
$ mvn cliean package
```
from a terminal.

This program has been tested with [UZ7HO's Soundmodem](https://uz7.ho.ua/packetradio.htm) and [QtSoundmodem](https://www.cantab.net/users/john.wiseman/Documents/QtSoundModem.html) connect to the KISS port and IP of your TNC.

```bash
usage: jaxt
Java AX25 Tool: A Java KISS TNC Client implementation
 -?,--help                 Shows help
 -d,--destination <arg>    Destination callsign
 -f,--config-file <arg>    Specify config file (.json)
 -h,--host <arg>           Specify TNC host (Default: 127.0.0.1)
 -l,--logging <arg>        Enable Logging, and optionally specify a
                           directory
 -m,--test-payload <arg>   Test payload to send on test interval. {{ts}}
                           for timestamp, {{seq}} for sequence.
 -p,--port <arg>           KISS Port (Default: 8100)
 -s,--source <arg>         Source callsign
 -t,--test <arg>           Send test packets (optional parameter interval
                           in seconds)
 -v,--verbose              Shows Packets
 -x,--post <arg>           HTTP POST packets received as JSON to url
 ```

 If you wish to avoid a lot of command line arguments, the config file format looks like this save as .json:

 ```json
{
    "host": "127.0.0.1",
    "port": 8100,
    "verbose": true,
    "source": "NOCALL",
    "destination": "NOCALL",
    "payload": "I am sending message #{{seq}} at {{ts}}",
    "txTest": false,
    "txTestInterval": 10000,
    "postUrl": "https://mywebsite.com/payloadhandler",
    "logPath": "./jaxt-logs/",
    "apiPort": 8101,
    "apiPassword": "locked-down-password1234"
}
 ```

 ### Logging

In the "logPath" (which defaults to the current directory) you will find "main.log", "exceptions.log", "rx.json" and "tx.json" As well as a .json file for every callsign you receive packets from.

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
    "source": "NOCALL1",
    "destination": "NOCALL2",
    "timestamp": 1686600213326,
    "payload": "This is a transmission",
    "path": ["WIDE1-1"]
}
```

### Websocket and HTTP API

if you use the -a option you can communicate with jaxt using websockets or http

Assuming the api port is set to 8101, You can connect to the server's websocket using:

ws://127.0.0.1:8101/jaxt/

once connected you must transmit an object containing {"apiPassword":"xxxxxxx"} before you will receive any packets or be able to transmit.

The format of AX.25 packets is the same as the POST packet format documented above. JSON objects should be sent as a single line.

You can also view packets using the simple HTTP interface at:

http://127.0.0.1:8101/