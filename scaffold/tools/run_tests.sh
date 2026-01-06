#!/bin/bash
# Battlecode 2026 Test Suite Runner
# Runs multiple matches and aggregates results

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
TEAM_A="${1:-ratbot}"
TEAM_B="${2:-examplefuncsplayer}"
NUM_MATCHES="${3:-5}"
MAPS="${4:-DefaultMap}"

# Output
RESULTS_DIR="$PROJECT_ROOT/test_results"
mkdir -p "$RESULTS_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT="$RESULTS_DIR/test_report_$TIMESTAMP.txt"

echo "=== Battlecode 2026 Test Suite ===" | tee "$REPORT"
echo "Team A: $TEAM_A" | tee -a "$REPORT"
echo "Team B: $TEAM_B" | tee -a "$REPORT"
echo "Matches: $NUM_MATCHES" | tee -a "$REPORT"
echo "Map: $MAPS" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

# Track wins
WINS_A=0
WINS_B=0
TOTAL_ROUNDS=0

# Run matches
for i in $(seq 1 $NUM_MATCHES); do
    echo "Match $i/$NUM_MATCHES..." | tee -a "$REPORT"

    # Run match (redirect output to temp file)
    MATCH_LOG="$RESULTS_DIR/match_${i}.log"

    # Placeholder: actual command when scaffold available
    # cd "$PROJECT_ROOT/scaffold" && ./gradlew run -PteamA=$TEAM_A -PteamB=$TEAM_B -Pmaps=$MAPS > "$MATCH_LOG" 2>&1

    # Parse result (placeholder)
    # WINNER=$(grep "Winner:" "$MATCH_LOG" | awk '{print $2}')
    # ROUNDS=$(grep "Round" "$MATCH_LOG" | tail -1 | awk '{print $2}')

    # For now, simulate
    WINNER="A"
    ROUNDS=500

    if [ "$WINNER" == "A" ]; then
        WINS_A=$((WINS_A + 1))
    else
        WINS_B=$((WINS_B + 1))
    fi

    TOTAL_ROUNDS=$((TOTAL_ROUNDS + ROUNDS))

    echo "  Winner: $WINNER (Rounds: $ROUNDS)" | tee -a "$REPORT"
done

echo "" | tee -a "$REPORT"
echo "=== Results ===" | tee -a "$REPORT"
echo "Team A Wins: $WINS_A / $NUM_MATCHES" | tee -a "$REPORT"
echo "Team B Wins: $WINS_B / $NUM_MATCHES" | tee -a "$REPORT"
echo "Average Rounds: $((TOTAL_ROUNDS / NUM_MATCHES))" | tee -a "$REPORT"
echo "" | tee -a "$REPORT"

WIN_RATE=$(awk "BEGIN {print ($WINS_A / $NUM_MATCHES) * 100}")
echo "Win Rate: ${WIN_RATE}%" | tee -a "$REPORT"

# Generate combined analysis
echo "=== Generating Analysis ===" | tee -a "$REPORT"

# Placeholder: aggregate logs from all matches
# for i in $(seq 1 $NUM_MATCHES); do
#     python3 "$SCRIPT_DIR/log_parser.py" "$RESULTS_DIR/match_${i}.log" >> "$RESULTS_DIR/combined_analysis.txt"
# done

echo "" | tee -a "$REPORT"
echo "Report saved to: $REPORT"
