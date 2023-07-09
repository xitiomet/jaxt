#!/bin/bash
killall -9 -q rtl_fm
bash -c 'rtl_fm -M wbfm -f '$1' -r 24k -d 0 | sox -t raw -r 24k -e signed-integer -b 16 -c 1 - -t raw -r 44100 -b 16 -c 1 -' > /dev/stdout
