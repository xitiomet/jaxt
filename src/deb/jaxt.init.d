#!/bin/bash
### BEGIN INIT INFO
# Provides:          jaxt
# Required-Start:    $local_fs $remote_fs $network
# Required-Stop:     $local_fs $remote_fs $network
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: jaxt Websocket Server
# Description:       Start the jaxt Websocket server
#  This script will start the jaxt web server.
### END INIT INFO

PIDFILE=/var/run/jaxt.pid
USER=jaxt
GROUP=jaxt
CWD=/var/log/jaxt
JVM_ARGS=
JAR_PATH=/usr/share/java/jaxt.jar
PROGRAM=/usr/bin/java
PROGRAM_ARGS="$JVM_ARGS -jar $JAR_PATH -l /var/log/jaxt -f /etc/jaxt/jaxt.json -c /etc/jaxt/commands.json"

start() {
    echo -n "Starting jaxt Server...."
    start-stop-daemon --start --make-pidfile --pidfile $PIDFILE --chuid $USER --user $USER --group $GROUP --chdir $CWD --umask 0 --exec $PROGRAM --background -- $PROGRAM_ARGS
    echo DONE
}

stop() {
    echo -n "Stopping jaxt Server...."
    start-stop-daemon --stop --pidfile $PIDFILE --user $USER --exec $PROGRAM --retry=TERM/30/KILL/5
    echo DONE
}

status() {
    start-stop-daemon --start --test --oknodo --pidfile $PIDFILE --user $USER --exec $PROGRAM
}

case "$1" in 
    start)
       start
       ;;
    stop)
       stop
       ;;
    restart)
       stop
       start
       ;;
    status)
       status
       ;;
    *)
       echo "Usage: $0 {start|stop|status|restart}"
esac

exit 0 
