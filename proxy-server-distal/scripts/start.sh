#!/bin/sh
source /etc/profile
cd `dirname $0`
cd ..
DEPLOY_DIR=`pwd`

LOGS_DIR=$DEPLOY_DIR/logs
if [ ! -d $LOGS_DIR ]; then
    mkdir $LOGS_DIR
fi
STDOUT_FILE=$LOGS_DIR/stdout.log

CONF_DIR=$DEPLOY_DIR/conf
LIB_DIR=$DEPLOY_DIR/lib
MAIN_DIR=$DEPLOY_DIR/main
MAIN_JARS=`ls $MAIN_DIR | grep .jar |awk '{print "'$MAIN_DIR'/"$0}'`
if [ $(echo $MAIN_JARS | wc -l) -gt 1 ];then
  echo "main dir not allow contain multi jar"
  exit
fi

KILLPID=`ps -ef | grep java | grep "$DEPLOY_DIR" | awk '{print $2}'`
if [ $KILLPID ]; then
  echo 'kill current running pid:'$KILLPID
  kill $KILLPID
  sleep 1
fi

JAVA_OPTS=" -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8"
if [ $1 ]; then
 JAVA_OPTS="$JAVA_OPTS -DcareMids=$1"
fi
JAVA_MEM_OPTS=" -server -Xms500m -Xmx800m  -XX:MaxNewSize=256m "

echo  "OPTS: $JAVA_OPTS   $JAVA_MEM_OPTS"
echo "STDOUT: $STDOUT_FILE"

RUN_COMMAND="nohup java -jar $JAVA_OPTS $JAVA_MEM_OPTS  -Dloader.path=$CONF_DIR,$LIB_DIR $MAIN_JARS $1 > $STDOUT_FILE 2>&1 &"

echo "RUN_COMMAND: "$RUN_COMMAND
eval $RUN_COMMAND

echo "START SUCCESS!"
PIDS=`ps -f | grep java | grep "$DEPLOY_DIR" | awk '{print $2}'`
echo "PID: $PIDS"
