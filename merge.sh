#!/bin/bash
set -e
if [ $# != 2 ]; then
    echo 1>&2 "Usage: $0 <remote> <pr>"
    exit 1
fi
#BRANCH=$1
BRANCH=$(git rev-parse --abbrev-ref HEAD)
REMOTE=$1
PR=$2
echo "Merging $PR from $REMOTE onto $BRANCH"
#FETCH_URL=`git remote show $REMOTE|grep "Fetch URL"|cut -c14-`
FETCH_URL=`git remote -v|grep $REMOTE|grep "fetch"|cut -f2|cut -d" " -f1`
OWNER=`echo $FETCH_URL | cut -d: -f2 | cut -d/ -f4`
REPO=`echo $FETCH_URL | cut -d/ -f5`
PULL=`curl -s -n https://api.github.com/repos/$OWNER/$REPO/pulls/$PR`
#PULL=`cat /tmp/out`
PULL=$PULL
MSG="Merge pull request #$PR from $(echo $PULL | jq -r .head.label)
$(echo $PULL | jq -r .title)"
echo "$MSG"
#echo -n "Continue..." && read _
set -x
git fetch $REMOTE pull/$PR/head
git merge --no-ff -m "$MSG" FETCH_HEAD
