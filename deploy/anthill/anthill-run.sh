#!/bin/sh
# Brain/Out dedicated-server launcher used as the Anthill deployment "binary".
#
# The Anthill game-controller spawns this as:  run.sh <sock> <ports> <args...>
# and we forward everything to the server jar. java.io.tmpdir is forced to an
# exec-able dir because the controller mounts /tmp as a noexec tmpfs and the
# online controller (anthill-runtime) loads a native JZMQ .so from tmp.
exec java -XX:-OmitStackTraceInFastThrow -server \
    -XX:MaxHeapFreeRatio=15 -XX:MinHeapFreeRatio=15 -XX:+UseG1GC \
    -Xms16m -Xmx256m -XX:InitiatingHeapOccupancyPercent=20 \
    -Djava.net.preferIPv4Stack=true -Djava.io.tmpdir=/opt/brainout-tmp \
    -jar brainout-server.jar "$@"
