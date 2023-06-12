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
    "testPayload": "I am sending message #{{seq}} at {{ts}}",
    "txTest": false,
    "txTestDelay": 10000,
    "postUrl": "https://mywebsite.com/payloadhandler",
    "logPath": "./jaxt-logs/"
}
 ```

 ### Logging

In the "logPath" (which defaults to the current directory) you will find "main.log", "exceptions.log", "rx.json" and "tx.json" As well as a .json file for every callsign you receive packets from.

* "main.log" - this log contains connection information, and status information.
* "exceptions.log" - this log contains advanced debugging info for jaxt.
* "rx.json" - A Log of all packets received using jaxt, each line represents one json object. Check out [JSON-Roller](https://openstatic.org/projects/json-roller/) for help parsing and filtering this information.
* "tx.json" - A Log of all packets transmitted using jaxt, each line represents one json object. Check out [JSON-Roller](https://openstatic.org/projects/json-roller/) for help parsing and filtering this information.