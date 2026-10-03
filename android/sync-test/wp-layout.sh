#!/bin/bash
echo "== WP block structure around content =="
grep -oE 'class="[^"]*(entry-content|wp-block-post-content|is-layout-constrained|wp-site-blocks|wp-block-group|has-global-padding)[^"]*"' /tmp/s1.html | sort | uniq -c | head -20
echo "== kl-wrap line context =="
grep -o '<main[^>]*>\|<div class="entry-content[^"]*">\|<div class="wp-block-post-content[^"]*">\|wp-site-blocks' /tmp/s1.html | head -10
echo "== css custom props (theme width) =="
grep -oE '\-\-wp--style--global--content-size:[^;]*|\-\-wp--preset--spacing--[a-z0-9]+: *[0-9.-]+px' /tmp/s1.html | sort -u | head