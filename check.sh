#!/bin/bash

file="$1"

all_valid=true
line_number=0

while IFS= read -r line; do
    line_number=$((line_number + 1))

    # Remove whitespace
    stripped=$(echo "$line" | tr -d '[:space:]')

    # Skip empty lines if desired (optional)
    [ -z "$stripped" ] && continue

    # Check if all characters are identical
    if ! echo "$stripped" | grep -Eq '^([0-9])\1*$'; then
        ech
