#!/bin/bash
. $(dirname $0)/config.sh


for HOST in $SERVER_HOSTS; do
	echo $HOST
    rsync -rcLv $RSYNC_OPTS $1 $REMOTE_USER@$HOST:$2 &
done

wait
