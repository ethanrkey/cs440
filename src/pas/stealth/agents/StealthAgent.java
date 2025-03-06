package src.pas.stealth.agents;




// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;




import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;




// JAVA PROJECT IMPORTS
import edu.bu.pas.stealth.agents.AStarAgent; // the base class of your class
import edu.bu.pas.stealth.agents.AStarAgent.AgentPhase; // INFILTRATE/EXFILTRATE enums for your state machine
import edu.bu.pas.stealth.agents.AStarAgent.ExtraParams; // base class for creating your own params objects
import edu.bu.pas.stealth.graph.Vertex; // Vertex = coordinate
import edu.bu.pas.stealth.graph.Path; // see the documentation...a Path is a linked list
import edu.bu.pas.stealth.agents.DestroyTownhallTestAgent; 




public class StealthAgent
       extends AStarAgent {


   // Fields of this class
   // TODO: add your fields here! For instance, it might be a good idea to
   // know when you've killed the enemy townhall so you know when to escape!
   // TODO: implement the state machine for following a path once we calculate it
   // this will for sure adding your own fields.
   private enum State 
   {
       IDLE,
       MOVING_TO_TOWNHALL,
       AVOIDING,
       REACHED_GOAL, 
       GOING_TO_GOLD
   }




   private State currentState;
   private List<Vertex> path;
   private int currentPathIndex;
   Queue<Vertex> queue = new LinkedList<>();
   Set<Vertex> visited = new HashSet<>();
   Map<Vertex, Path> parentMap = new HashMap<>();
   private Vertex startingPosition;
   private Vertex goldResource;
   private int townhallCount;
   private int escapingCount;


   boolean enemyTownhallKilled = false;


   private int enemyChebyshevSightLimit;


   public StealthAgent(int playerNum) {
       super(playerNum);
       this.currentState = State.IDLE;
       this.path = new ArrayList<>();
       this.enemyChebyshevSightLimit = -1; // invalid value....we won't know this until initialStep()
   }


   // TODO: add some getter methods for your fields! Thats the java way to do
   // things!
   public final int getEnemyChebyshevSightLimit() {
       return this.enemyChebyshevSightLimit;
   }


   public void setEnemyChebyshevSightLimit(int i) {
       this.enemyChebyshevSightLimit = i;
   }


   public boolean isEnemyTownhallKilled() {
       return this.enemyTownhallKilled;
   }


   ///////////////////////////////////////// Sepia methods to override
   ///////////////////////////////////////// ///////////////////////////////////


   /**
    * TODO: if you add any fields to this class it might be a good idea to
    * initialize them here
    * if they need sepia information!
    */
   @Override
   public Map<Integer, Action> initialStep(StateView state,
           HistoryView history) {
       super.initialStep(state, history); // call AStarAgent's initialStep() to set helpful fields and stuff


    goldResource = findGoldResource(state);
    if (goldResource == null) {
        System.out.println("Gold resource not found");
    }
       int startId = getMyUnitID();
       UnitView start = state.getUnit(startId);
       int startX = start.getXPosition();
       int startY = start.getYPosition();
       startingPosition = new Vertex(startX, startY);
   
   
       System.out.println("Stored starting position: (" + startX + ", " + startY + ")");

       // now some fields are set for us b/c we called AStarAgent's initialStep()
       // let's calculate how far away enemy units can see us...this will be the same
       // for all units (except the base)
       // which doesn't have a sight limit (nor does it care about seeing you)
       // iterate over the "other" (i.e. not the base) enemy units until we get a
       // UnitView that is not null
       UnitView otherEnemyUnitView = null;
       Iterator<Integer> otherEnemyUnitIDsIt = this.getOtherEnemyUnitIDs().iterator();
       while (otherEnemyUnitIDsIt.hasNext() && otherEnemyUnitView == null) {
           otherEnemyUnitView = state.getUnit(otherEnemyUnitIDsIt.next());
       }


       if (otherEnemyUnitView == null) {
           System.err.println("[ERROR] StealthAgent.initialStep: could not find a non-null 'other' enemy UnitView??");
           System.exit(-1);
       }


       // lookup an attribute from the unit's "template" (which you can find in the map
       // .xml files)
       // When I specify the unit's (i.e. "footman"'s) xml template, I will use the
       // "range" attribute
       // as the enemy sight limit
       this.setEnemyChebyshevSightLimit(otherEnemyUnitView.getTemplateView().getRange());




       return null;
   }


   /**
    * TODO: implement me! This is the method that will be called every turn of the
    * game.
    * This method is responsible for assigning actions to all units that you
    * control
    * (which should only be a single footman in this game)
    */
   @Override
public Map<Integer, Action> middleStep(StateView state, HistoryView history) {
    Map<Integer, Action> actions = new HashMap<>();
    int myUnitId = getMyUnitID();
    UnitView unit = state.getUnit(myUnitId);
    int src_x = unit.getXPosition();
    int src_y = unit.getYPosition();
    Vertex src = new Vertex(src_x, src_y);
    System.out.println("Current state: " + currentState);
    switch (currentState) {
        case IDLE:
            currentState = State.GOING_TO_GOLD;
            break;
        case GOING_TO_GOLD:

           // System.out.println("Gold resource: " + goldResource.getXCoordinate() + " " + goldResource.getYCoordinate());
           System.out.println(isGoldExhausted(state));
            if(isGoldExhausted(state)) {
                currentState = State.MOVING_TO_TOWNHALL;
                System.out.println("We have taken all their gold");
                break;
            }
          
            if (path.isEmpty() || shouldReplacePlan(state, null)) {
           
                Path pathToGold = aStarSearch(src, goldResource, state, null);
                if (pathToGold != null) {
                    System.out.println("Path to gold found");
                    path = makePath(pathToGold);
                    currentPathIndex = 0;
                } else {
                    System.out.println("Path to gold not found");
                }
            }
            
            // Follow the path
            if (!path.isEmpty() && currentPathIndex < path.size() - 1) {
                Vertex current = path.get(currentPathIndex);
                Vertex next = path.get(currentPathIndex + 1);
                Direction direction = getDirection(current.getXCoordinate(), current.getYCoordinate(), next.getXCoordinate(), next.getYCoordinate(), state);
                if (direction != null)
                {
                    actions.put(getMyUnitID(), Action.createPrimitiveMove(getMyUnitID(), direction));
                    currentPathIndex++;
                }
            }
            int distance = Math.max(Math.abs(src_x - goldResource.getXCoordinate()), Math.abs(src_y - goldResource.getYCoordinate()));
            if(distance == 1) {
                actions.put(getMyUnitID(), Action.createPrimitiveGather(getMyUnitID(), getDirection(src_x, src_y, goldResource.getXCoordinate(), goldResource.getYCoordinate(), state)));
                System.out.println("Gathering gold");
            }
            break;
            
        case MOVING_TO_TOWNHALL:
            townhallCount++;
            int goalId = getEnemyBaseUnitID();
            UnitView goal = state.getUnit(goalId);
            if(goal == null) {
                enemyTownhallKilled = true;
                currentState = State.REACHED_GOAL;
                System.out.println("Enemy townhall killed");
                break;
            }
            int townhall_x = goal.getXPosition();
            int townhall_y = goal.getYPosition();
            Vertex townhall = new Vertex(townhall_x, townhall_y);
            
            if (path.isEmpty() || shouldReplacePlan(state, null) || townhallCount == 1) {
                System.out.println("first if clause");
                Path pathToTownhall = aStarSearch(src, townhall, state, null);
                if (pathToTownhall != null) {
                    path = makePath(pathToTownhall); 
                    currentPathIndex = 0;
                }
            }
        
            // Follow the path
            if (!path.isEmpty() && currentPathIndex < path.size() - 1) {
                System.out.println("entered move if clause");
                Vertex current = path.get(currentPathIndex);
                Vertex next = path.get(currentPathIndex + 1);
                Direction direction = getDirection(current.getXCoordinate(), current.getYCoordinate(), next.getXCoordinate(), next.getYCoordinate(), state);
                if (direction != null) {
                    actions.put(getMyUnitID(), Action.createPrimitiveMove(getMyUnitID(), direction));
                    currentPathIndex++;
                    System.out.println("added move to actions");
                }
            }
            int length = Math.max(Math.abs(src_x - townhall_x), Math.abs(src_y - townhall_y));
            System.out.println("Distance to townhall: " + length);
            if (length == 1) 
            {
                actions.put(getMyUnitID(), Action.createPrimitiveAttack(getMyUnitID(), goalId));
                System.out.println("Attacking the townhall");
            }
         
          
            break;
        case AVOIDING:
            break;


        case REACHED_GOAL:
        escapingCount++;
          System.out.println("Escaping" + escapingCount);

          System.out.println("Starting position: " + startingPosition.getXCoordinate() + " " + startingPosition.getYCoordinate());
            
        if (path.isEmpty() || shouldReplacePlan(state, null) || escapingCount == 1) {
            System.out.println("first escaping if clause");
            Path pathToTownhall = aStarSearch(src, startingPosition, state, null);
            if (pathToTownhall != null) {
                path = makePath(pathToTownhall); 
                currentPathIndex = 0;
            }
        }
    
        // Follow the path
        if (!path.isEmpty() && currentPathIndex < path.size() - 1) {
            System.out.println("entered escaping move if clause");
            Vertex current = path.get(currentPathIndex);
            Vertex next = path.get(currentPathIndex + 1);
            Direction direction = getDirection(current.getXCoordinate(), current.getYCoordinate(), next.getXCoordinate(), next.getYCoordinate(), state);
            if (direction != null) {
                actions.put(getMyUnitID(), Action.createPrimitiveMove(getMyUnitID(), direction));
                currentPathIndex++;
                System.out.println("added escaping move to actions");
            }
        }
            break;
    
        default:
            break;
    }

    return actions;
}


   ////////////////////////////////// End of Sepia methods to override
   ////////////////////////////////// //////////////////////////////////
  
   /////////////////////////////////// AStarAgent methods to override
   /////////////////////////////////// ///////////////////////////////////
  
   public boolean isValidPostion(int x, int y, StateView state) {
       if (!(state.inBounds(x, y))) {
           return false;
       }


       for (Integer resourceId : state.getAllResourceIds()) {
           ResourceView resource = state.getResourceNode(resourceId);
            if(resource.getType().name().equals("GOLD_MINE")) {
                continue;
            }
           //System.out.println (resource.getXPosition() + " " + resource.getYPosition());
           if (resource.getXPosition() == x && resource.getYPosition() == y) {
               return false;
           }
       }


       return true;
   }

   public Vertex findGoldResource(StateView state) {
    for (Integer resourceId : state.getAllResourceIds()) {
        ResourceView resource = state.getResourceNode(resourceId);
       
        if (resource.getType().name().equals("GOLD_MINE")) {
            System.out.println("Gold mine found");
            return new Vertex(resource.getXPosition(), resource.getYPosition());
        }
    }
    return null;
    }

    private boolean isGoldExhausted(StateView state) {
        for (Integer resourceId : state.getAllResourceIds()) {
            ResourceView resource = state.getResourceNode(resourceId);
        
            if (resource != null && resource.getType().name().equalsIgnoreCase("GOLD_MINE")) {
                // If at least one gold mine is found, then there is still gold available.
                return false;
            }
        }
        // No gold mine resource found means all gold has been gathered.
        return true;
    }


   public Collection<Vertex> getNeighbors(Vertex v, StateView state, ExtraParams extraParams)
    {
       List<Vertex> neighbors = new ArrayList<>();
 
       for (Direction direction : Direction.values())
       {
           int newX = v.getXCoordinate() + direction.xComponent();
           int newY = v.getYCoordinate() + direction.yComponent();
          
           if (isValidPostion(newX, newY, state)) {
               neighbors.add(new Vertex(newX, newY));
           }
       }
       return neighbors;
   }
   public Path aStarSearch(Vertex src, Vertex dst, StateView state, ExtraParams extraParams) {
    PriorityQueue<Path> openSet = new PriorityQueue<>
    (Comparator.comparingDouble(Path::getEstimatedPathCostToGoal));
    Map<Vertex, Path> cameFrom = new HashMap<>();
    Map<Vertex, Double> gScore = new HashMap<>();
    Map<Vertex, Double> fScore = new HashMap<>();

    Path startPath = new Path(src, 0, getHeuristicValue(src, dst, state), null);
    openSet.add(startPath);
    gScore.put(src, 0.0);
    fScore.put(src, (double) getHeuristicValue(src, dst, state)); 

    while (!openSet.isEmpty()) {
        Path currentPath = openSet.poll();
        Vertex current = currentPath.getDestination();

        if (current.equals(dst)) {
            return currentPath;
        }

        for (Vertex neighbor : getNeighbors(current, state, extraParams)) {
            double tentativeGScore = gScore.get(current) + getEdgeWeight(current, neighbor, state, extraParams);
            if (tentativeGScore < gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
                gScore.put(neighbor, tentativeGScore);
                double estimatedCostToGoal = tentativeGScore + getHeuristicValue(neighbor, dst, state);
                Path newPath = new Path(neighbor, (float) tentativeGScore, (float) estimatedCostToGoal, currentPath);
                cameFrom.put(neighbor, newPath);
                fScore.put(neighbor, estimatedCostToGoal);
                openSet.add(newPath);
            }
        }
    }
    return null;
}

    public float getEdgeWeight(Vertex src, Vertex dst, StateView state, ExtraParams extraParams) {
        float baseWeight = getHeuristicValue(src, dst, state);
        float nearestEnemyDistance = Float.MAX_VALUE;

        for (Integer enemyUnitID : getOtherEnemyUnitIDs()) {
            UnitView enemyUnit = state.getUnit(enemyUnitID);
            if (enemyUnit == null) {
                // This unit no longer exists. Skip it.
                continue;
            }
            int enemyX = enemyUnit.getXPosition();
            int enemyY = enemyUnit.getYPosition();
            int chebyshevDistance = Math.max(Math.abs(dst.getXCoordinate() - enemyX), Math.abs(dst.getYCoordinate() - enemyY));
            if (chebyshevDistance < nearestEnemyDistance) {
                nearestEnemyDistance = chebyshevDistance;
            }
        }

        if (nearestEnemyDistance <= enemyChebyshevSightLimit+1) {
            baseWeight *= Math.exp( enemyChebyshevSightLimit + 1);
        }

        if (nearestEnemyDistance <= enemyChebyshevSightLimit + 3) {
            baseWeight *= 10;
        }

        return baseWeight;
    }

   public boolean shouldReplacePlan(StateView state, ExtraParams extraParams) {
   
    if (path.isEmpty()) {
        return true;
    }

    int myUnitId = getMyUnitID();
    UnitView unit = state.getUnit(myUnitId);
    int src_x = unit.getXPosition();
    int src_y = unit.getYPosition();
    int enemyCount = 0;

    //check if there are enemy units within the sight limit of the unit
    for (Integer enemyUnitID : getOtherEnemyUnitIDs()) {
        enemyCount++;
        System.out.println("enemies: " + enemyCount);
        UnitView enemyUnit = state.getUnit(enemyUnitID);
        if (enemyUnit == null) {
            // This means the enemy is gone. Skip it to avoid NullPointerException
            continue;
        }
        int enemyX = enemyUnit.getXPosition();
        int enemyY = enemyUnit.getYPosition();
        //System.out.println("Enemy position: " + enemyX + " " + enemyY);
        int distance = Math.max(Math.abs(src_x - enemyX), Math.abs(src_y - enemyY));
        //System.out.println("Enemy distance: " + distance);
        if (distance <= enemyChebyshevSightLimit + 1) {
           // System.out.println(enemyChebyshevSightLimit);  
           // System.out.println("Enemy sighted");
            return true;
        }
    }

    return false;
    }

   public Direction getDirection(int src_x, int src_y, int dest_x, int dest_y, StateView state) {
     
       for (Direction direction : Direction.values())
       {
           int newX = src_x + direction.xComponent();
           int newY = src_y + direction.yComponent();
           if (newX == dest_x && newY == dest_y)
           {
               return direction;
           }
       }
       return null;
   }

    private List<Vertex> makePath(Path pathToTownhall) {
        List<Vertex> path = new ArrayList<>();
        Path currentPath = pathToTownhall;

        while (currentPath != null) {
            path.add(currentPath.getDestination());
            currentPath = currentPath.getParentPath();
        }

        Collections.reverse(path);
        return path;
    }
  
   //////////////////////////////// End of AStarAgent methods to override
   //////////////////////////////// ///////////////////////////////


}