#!/bin/bash

#CONFIG
IDENTITY_ENDPOINT="<keystone>"
TENANT="<tenant>"
USERNAME="<user>"
PASSWORD="<password>"
TOKEN=""
SWIFT_ENDPOINT="<swift>"
JMO_CONTAINER="jmo-repository"
#----------------------------------------- FUNCTIONS -----------------------------------------------
function getToken(){
	curl -s -X POST $IDENTITY_ENDPOINT/tokens \
		-H "Content-Type: application/json" \
		--data '{"auth": {"tenantName": "'$TENANT'", "passwordCredentials": {"username": "'$USERNAME'", "password": "'$PASSWORD'"}}}' \
		| grep -o '"token".*' | grep -m 1 -o '"id".*' | cut -d ',' -f 1 | cut -d '"' -f 4 
}

function downloadFile(){
	_REMOTE=$1
	_LOCAL=$2

	_CONTAINER=$(echo $_REMOTE | cut -d "/" -f 1)
	_OBJECT=$(echo $_REMOTE | cut -d "/" -f 2-) # possibile creare anche subfolder (2-)

	curl -v -H "X-Auth-Token:$TOKEN" $SWIFT_ENDPOINT/$JMO_CONTAINER/$_OBJECT > $_LOCAL
}	

# --------------------------------------------------------------------------------------------------
TOKEN=$(getToken)
mkdir JMO JMO/plugins JMO/scripts
cd JMO
downloadFile $JMO_CONTAINER/jmo-monitor.jar jmo-monitor.jar
downloadFile $JMO_CONTAINER/JMO-config.properties JMO-config.properties
#------------------------------------------------------------Plugin picking------------------------
#Please, comment the lines referencing plugins you do NOT want to pick

<plugins>
#---------------------------------------------------------End Plugin Picking-----------------------
#Start JMO
java -jar jmo-monitor.jar &
