#!/bin/bash

# Set paths
SEPIA_JAR="./lib/*"
GAME_XML="data/pas/stealth/BigMaze.xml"
LOG_FILE="sepia_results.log"
SUCCESS_COUNT=0
TOTAL_RUNS=300

# Clear log file
> "$LOG_FILE"

echo "Running SEPIA AI Agent 300 times..."
for i in $(seq 1 $TOTAL_RUNS); do
    echo "Run #$i" >> "$LOG_FILE"
    
    # Run the game and capture the output
    java -cp "$SEPIA_JAR:." edu.cwru.sepia.Main2 "$GAME_XML" >> "$LOG_FILE" 2>&1

    # Check if the run was successful (modify this based on SEPIA's success output)
    if grep -q "The enemy was destroyed, you win!" "$LOG_FILE"; then
        ((SUCCESS_COUNT++))
    fi
done

# Display final results
echo "Agent completed $SUCCESS_COUNT out of $TOTAL_RUNS runs successfully."
echo "Final Results: $SUCCESS_COUNT / $TOTAL_RUNS" >> "$LOG_FILE"

# Done!
echo "Results saved in $LOG_FILE."
