#!/bin/bash
ls -la /tmp/wp.tar.gz 2>/dev/null || echo "no wp.tar.gz"
ps aux | grep -E 'curl|tar|wp-cli' | grep -v grep | head
ls /tmp/wpsrc 2>/dev/null || echo "no wpsrc"
df -h /tmp | tail -1