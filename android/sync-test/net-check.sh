#!/bin/bash
echo "== reachability =="
for url in https://github.com https://codeload.github.com https://www.google.com https://api.wordpress.org https://wordpress.org; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 "$url" 2>/dev/null)
  echo "$url -> $code"
done
echo "== resolve =="
getent hosts github.com wordpress.org | head -4
echo "== wp tag archives reachability =="
code=$(curl -sL -o /dev/null -w '%{http_code}' --max-time 20 https://codeload.github.com/WordPress/WordPress/tar.gz/refs/tags/6.7.2 2>/dev/null)
echo "codeload WP 6.7.2 -> $code"