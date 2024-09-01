#!/bin/bash

# Check if the JAR file is provided
if [ -z "$1" ]; then
  echo "Usage: $0 your-jar-file.jar output-name"
  exit 1
fi

if [ -z "$2" ]; then
  echo "Usage: $0 your-jar-file.jar output-name"
  exit 1
fi


JAR_FILE="$1"
OUTPUT_FILE="$2"

# Create the launcher script
LAUNCHER_SCRIPT=$(mktemp)
cat << 'EOF' > "$LAUNCHER_SCRIPT"
#!/bin/sh
exec java -jar "$0" "$@"
exit 0
EOF

# Concatenate the launcher script with the JAR file
cat "$LAUNCHER_SCRIPT" "$JAR_FILE" > "$OUTPUT_FILE"

# Make the output file executable
chmod +x "$OUTPUT_FILE"

# Clean up the temporary launcher script
rm "$LAUNCHER_SCRIPT"

echo "$OUTPUT_FILE"

