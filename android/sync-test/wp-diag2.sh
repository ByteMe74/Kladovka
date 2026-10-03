#!/bin/bash
set -x
echo A
ls -la /tmp/wp.tar.gz
echo B
[ -s /tmp/wp.tar.gz ] && echo exists || echo missing
echo C
cd /tmp
rm -rf wpsrc && mkdir wpsrc
echo D
tar -tzf /tmp/wp.tar.gz >/dev/null 2>&1 && echo TARGZ-OK || echo TARGZ-BAD
echo E