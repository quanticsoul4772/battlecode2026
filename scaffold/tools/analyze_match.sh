#!/bin/bash
# Battlecode 2026 Match Analysis
# Runs match and generates comprehensive analysis report

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Colors for output (if terminal supports)
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
TEAM_A="${1:-ratbot}"
TEAM_B="${2:-examplefuncsplayer}"
MAP="${3:-DefaultMap}"
OUTPUT_DIR="$PROJECT_ROOT/analysis"

echo -e "${GREEN}=== Battlecode 2026 Match Analysis ===${NC}"
echo "Team A: $TEAM_A"
echo "Team B: $TEAM_B"
echo "Map: $MAP"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Find latest match file
MATCH_FILE=$(ls -t "$PROJECT_ROOT"/matches/*.bc26 2>/dev/null | head -1)

if [ -z "$MATCH_FILE" ]; then
    echo -e "${RED}No match files found in matches/${NC}"
    echo "Run a match first: ./gradlew run -PteamA=$TEAM_A -PteamB=$TEAM_B -Pmaps=$MAP"
    exit 1
fi

echo "Analyzing: $MATCH_FILE"
echo ""

# Extract logs from match file (format TBD - this is placeholder)
# In real implementation, would parse .bc26 binary format
LOG_FILE="$OUTPUT_DIR/match_logs.txt"

# Placeholder: extract stdout from match
# TODO: Update when we know .bc26 format
echo -e "${YELLOW}Note: Log extraction needs .bc26 format specification${NC}"
echo ""

# If logs available, analyze them
if [ -f "$LOG_FILE" ]; then
    echo -e "${GREEN}=== Log Analysis ===${NC}"
    python3 "$SCRIPT_DIR/log_parser.py" "$LOG_FILE" "$OUTPUT_DIR/match_data.csv"
    echo ""
fi

# Generate summary report
REPORT_FILE="$OUTPUT_DIR/match_report.txt"

cat > "$REPORT_FILE" << EOF
Battlecode 2026 Match Analysis Report
Generated: $(date)

Match: $TEAM_A vs $TEAM_B on $MAP
Match File: $MATCH_FILE

=== Analysis ===

EOF

# Append parsed analysis if available
if [ -f "$LOG_FILE" ]; then
    python3 "$SCRIPT_DIR/log_parser.py" "$LOG_FILE" >> "$REPORT_FILE"
fi

cat >> "$REPORT_FILE" << EOF

=== Files Generated ===
- Match logs: $LOG_FILE
- CSV export: $OUTPUT_DIR/match_data.csv
- This report: $REPORT_FILE

EOF

echo -e "${GREEN}Report generated: $REPORT_FILE${NC}"
cat "$REPORT_FILE"
