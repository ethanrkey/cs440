package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;


import java.util.HashSet;   // will need for dfs
import java.util.Stack;     // will need for dfs
import java.util.Set;       // will need for dfs


// JAVA PROJECT IMPORTS


public class DFSMazeAgent
    extends MazeAgent
{

    public DFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }

    @Override
public Path search(Vertex src, Vertex goal, StateView state) {
    Stack<Path> stack = new Stack<>();
    Set<String> visited = new HashSet<>(); // Use (x, y) as a key to track visited nodes

    stack.push(new Path(src));
    visited.add(src.getXCoordinate() + "," + src.getYCoordinate());

    // Define movement directions (cardinal + diagonal)
    int[][] directions = {
        {0, 1}, {0, -1}, {1, 0}, {-1, 0}, // Cardinal directions
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1} // Diagonal directions
    };

    while (!stack.isEmpty()) {
        System.out.println("while loop started");
        Path currentPath = stack.pop();
        Vertex currentVertex = currentPath.getDestination();

        int x = currentVertex.getXCoordinate();
        int y = currentVertex.getYCoordinate();

        System.out.println("Checking Vertex: (" + x + ", " + y + ")");

        // **Fix: Compare goal using coordinates, not .equals()**
        if (x == goal.getXCoordinate() && y == goal.getYCoordinate()) {
            System.out.println("Path found to goal!");
            return currentPath;
        }

        // Explore all possible neighbors (depth-first)
        for (int[] dir : directions) {
            int newX = x + dir[0];
            int newY = y + dir[1];

            String posKey = newX + "," + newY; // Track visited by coordinate string

            System.out.println("Trying to move to: (" + newX + ", " + newY + ")");

            // **Fix: Ensure valid movement**
            if (state.inBounds(newX, newY)) {
                if (state.isResourceAt(newX, newY)) {
                    System.out.println("Blocked by tree at: (" + newX + ", " + newY + ")");
                    continue;
                }
                if (!visited.contains(posKey)) {
                    visited.add(posKey);  // **Fix: Mark visited here**
                    stack.push(new Path(new Vertex(newX, newY), 1f, currentPath));
                }
            }
        }
    }

    System.err.println("ERROR: DFS could not find a path from (" + src.getXCoordinate() + ", " + src.getYCoordinate() + ") to (" + goal.getXCoordinate() + ", " + goal.getYCoordinate() + ")");
    return null;
}


}
