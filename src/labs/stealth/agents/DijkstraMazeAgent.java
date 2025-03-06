package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;                           // Directions in Sepia


import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue; // heap in java
import java.util.Set;


// JAVA PROJECT IMPORTS


public class DijkstraMazeAgent
    extends MazeAgent
{

    public DijkstraMazeAgent(int playerNum)
    {
        super(playerNum);
    }
    @Override
public Path search(Vertex src, Vertex goal, StateView state) {
    PriorityQueue<Path> pq = new PriorityQueue<>(Comparator.comparingDouble(Path::getTrueCost));
    Map<Vertex, Float> costMap = new HashMap<>();
    Map<Vertex, Path> pathMap = new HashMap<>();
    Set<Vertex> visited = new HashSet<>();

    float horizontalCost = 5f;
    float verticalCost = 10f;
    float diagonalCost = (float) Math.sqrt(Math.pow(horizontalCost, 2) + Math.pow(verticalCost, 2));

    pq.add(new Path(src, 0f, null));
    costMap.put(src, 0f);
    pathMap.put(src, new Path(src, 0f, null));

    while (!pq.isEmpty()) {
        Path currentPath = pq.poll();
        Vertex currentVertex = currentPath.getDestination();
        float currentCost = currentPath.getTrueCost();

        if (visited.contains(currentVertex)) continue;
        visited.add(currentVertex);

        // ✅ Check if we reached the goal
        if (currentVertex.equals(goal)) {
            System.out.println("Dijkstra found path to goal with cost: " + currentCost);
            return currentPath;
        }

        for (Direction dir : Direction.values()) {
            int newX = currentVertex.getXCoordinate() + dir.xComponent();
            int newY = state.getYExtent() - (currentVertex.getYCoordinate() + dir.yComponent()); // ✅ Fix flipped Y-coordinates
            Vertex neighbor = new Vertex(newX, newY);

            // ✅ Skip invalid positions
            if (!state.inBounds(newX, newY) || state.isResourceAt(newX, newY)) {
                continue;
            }

            // ✅ Correct movement cost
            float edgeCost;
            if (dir == Direction.EAST || dir == Direction.WEST) {
                edgeCost = horizontalCost;
            } else if (dir == Direction.SOUTH) {
                edgeCost = 1f;
            } else if (dir == Direction.NORTH) {
                edgeCost = verticalCost;
            } else { // Diagonal movement cost
                edgeCost = diagonalCost;
            }

            System.out.println("Moving " + dir + " with calculated cost: " + edgeCost);

            float newCost = currentCost + edgeCost;

            // ✅ Update cost and path only if new path is cheaper
            if (!costMap.containsKey(neighbor) || newCost < costMap.get(neighbor)) {
                System.out.println("Updating cost of (" + newX + ", " + newY + ") to " + newCost);
                costMap.put(neighbor, newCost);
                Path newPath = new Path(neighbor, newCost, currentPath);
                pathMap.put(neighbor, newPath);
                pq.add(newPath);
            }
        }
    }

    System.err.println("ERROR: Dijkstra could not find a path from (" + src.getXCoordinate() + ", " + src.getYCoordinate() + ") to (" + goal.getXCoordinate() + ", " + goal.getYCoordinate() + ")");
    return null;
}

}
