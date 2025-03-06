package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;


import java.util.HashSet;       // will need for bfs
import java.util.Queue;         // will need for bfs
import java.util.LinkedList;    // will need for bfs
import java.util.Set;           // will need for bfs


// JAVA PROJECT IMPORTS


public class BFSMazeAgent
    extends MazeAgent
{

    public BFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }

    @Override
public Path search(Vertex src, Vertex goal, StateView state) {
    Queue<Path> queue = new LinkedList<>();
    Set<String> visited = new HashSet<>(); // Use (x, y) as string key

    queue.add(new Path(src));
    visited.add(src.getXCoordinate() + "," + src.getYCoordinate());

    int[][] directions = {
        {0, 1}, {0, -1}, {1, 0}, {-1, 0}, // Cardinal directions
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1} // Diagonal directions
    };

    System.out.println("Goal Vertex: (" + goal.getXCoordinate() + ", " + goal.getYCoordinate() + ")");

    while (!queue.isEmpty()) {
        Path currentPath = queue.poll();
        Vertex currentVertex = currentPath.getDestination();

        int x = currentVertex.getXCoordinate();
        int y = currentVertex.getYCoordinate();

        System.out.println("Checking Vertex: (" + x + ", " + y + ")");

        // **Correct Goal Check**
        if (x == goal.getXCoordinate() && y == goal.getYCoordinate()) {
            System.out.println("Path found to goal!");
            return currentPath;
        }

        for (int[] dir : directions) {
            int newX = x + dir[0];
            int newY = y + dir[1];

            String posKey = newX + "," + newY; // Use a string key to track visited

            // **Fix: Ensure valid movement**
            if (state.inBounds(newX, newY) &&
                !state.isResourceAt(newX, newY) && 
                !visited.contains(posKey)) {

                Vertex neighbor = new Vertex(newX, newY);
                visited.add(posKey); // **Fix: Mark visited here**
                queue.add(new Path(neighbor, 1f, currentPath));
            }
        }
    }

    System.err.println("ERROR: BFS could not find a path from (" + src.getXCoordinate() + ", " + src.getYCoordinate() + ") to (" + goal.getXCoordinate() + ", " + goal.getYCoordinate() + ")");
    return null;
}


}
