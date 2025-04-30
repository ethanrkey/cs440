package src.pas.tetris.agents;

import java.util.*;
// JAVA PROJECT IMPORTS
import edu.bu.pas.tetris.agents.QAgent;
import edu.bu.pas.tetris.agents.TrainerAgent.GameCounter;
import edu.bu.pas.tetris.game.Board;
import edu.bu.pas.tetris.game.Game.GameView;
import edu.bu.pas.tetris.game.minos.Mino;
import edu.bu.pas.tetris.game.minos.Mino.MinoType;
import edu.bu.pas.tetris.linalg.Matrix;
import edu.bu.pas.tetris.nn.Model;
import edu.bu.pas.tetris.nn.LossFunction;
import edu.bu.pas.tetris.nn.Optimizer;
import edu.bu.pas.tetris.nn.models.Sequential;
import edu.bu.pas.tetris.nn.layers.Dense; // fully connected layer
import edu.bu.pas.tetris.nn.layers.ReLU;  // some activations (below too)
import edu.bu.pas.tetris.nn.layers.Tanh;
import edu.bu.pas.tetris.nn.layers.Sigmoid;
import edu.bu.pas.tetris.training.data.Dataset;
import edu.bu.pas.tetris.utils.Pair;

public class TetrisQAgent
   extends QAgent
{

   private int fullRowsBeforeMove = 0;
   public static final double EXPLORATION_PROB = 0.05;
   private Random random;
   public TetrisQAgent(String name)
   {
       super(name);
       this.random = new Random(12345); // optional to have a seed
   }

   public Random getRandom() { return this.random; }

   @Override
   public Model initQFunction() {
       final int inputDim = 220;              // match the number of features you use
       final int hidden1 = 128;               // first hidden layer size
       final int hidden2 = 64;               // second hidden layer (optional but helpful)
       final int outputDim = 1;              // single Q-value output
 
       Sequential qFunction = new Sequential();
 
       // Input → Hidden Layer 1
       qFunction.add(new Dense(inputDim, hidden1));
       qFunction.add(new ReLU()); // fast and effective non-linearity
 
       // Hidden Layer 1 → Hidden Layer 2
       qFunction.add(new Dense(hidden1, hidden2));
       qFunction.add(new ReLU()); // deeper representation
 
       // Hidden Layer 2 → Output
       qFunction.add(new Dense(hidden2, outputDim));
 
       return qFunction;
   }

   /**
       This function is for you to figure out what your features
       are. This should end up being a single row-vector, and the
       dimensions should be what your qfunction is expecting.
       One thing we can do is get the grayscale image
       where squares in the image are 0.0 if unoccupied, 0.5 if
       there is a "background" square (i.e. that square is occupied
       but it is not the current piece being placed), and 1.0 for
       any squares that the current piece is being considered for.
     
       We can then flatten this image to get a row-vector, but we
       can do more than this! Try to be creative: how can you measure the
       "state" of the game without relying on the pixels? If you were given
       a tetris game midway through play, what properties would you look for?
    */
    @Override
    public Matrix getQFunctionInput(final GameView game, final Mino potentialAction) {
        try {
            Matrix simBoard = game.getGrayscaleImage(potentialAction); // simulated board after placing mino
            Matrix flattened = simBoard.flatten(); // this can throw an exception
    
            return flattened;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }
      

//    public int simulateRowsCleared(GameView game, Mino action) {
//     try {
//         Matrix newBoard = game.getGrayscaleImage(action);
//         return countFullRows(newBoard);
//     } catch (Exception e) {
//         e.printStackTrace();
//         return 0;
//     }
// }

// private int countFullRows(Matrix board) {
//     int count = 0;
//     int numRows = board.getShape().getNumRows();
//     int numCols = board.getShape().getNumCols();

//     for (int row = 0; row < numRows; row++) {
//         boolean isFull = true;
//         for (int col = 0; col < numCols; col++) {
//             if (board.get(row, col) == 0.0) {
//                 isFull = false;
//                 break;
//             }
//         }
//         if (isFull) count++;
//     }
//     return count;
// }
 
   /**
    * This method is used to decide if we should follow our current policy
    * (i.e. our q-function), or if we should ignore it and take a random action
    * (i.e. explore).
    *
    * Remember, as the q-function learns, it will start to predict the same "good" actions
    * over and over again. This can prevent us from discovering new, potentially even
    * better states, which we want to do! So, sometimes we should ignore our policy
    * and explore to gain novel experiences.
    *
    * The current implementation chooses to ignore the current policy around 5% of the time.
    * While this strategy is easy to implement, it often doesn't perform well and is
    * really sensitive to the EXPLORATION_PROB. I would recommend devising your own
    * strategy here.
    */
   @Override
   public boolean shouldExplore(final GameView game, final GameCounter gameCounter) {
       long gameNum = gameCounter.getCurrentCycleIdx();
       double startEpsilon = 0.4;
       double endEpsilon = 0.05;
       int decayDuration = 500; // number of games over which to decay
       double epsilon = Math.max(endEpsilon, startEpsilon - (startEpsilon - endEpsilon) * gameNum / decayDuration);
       return getRandom().nextDouble() <= epsilon;
       
   }

   /**
    * This method is a counterpart to the "shouldExplore" method. Whenever we decide
    * that we should ignore our policy, we now have to actually choose an action.
    *
    * You should come up with a way of choosing an action so that the model gets
    * to experience something new. The current implemention just chooses a random
    * option, which in practice doesn't work as well as a more guided strategy.
    * I would recommend devising your own strategy here.
    */
   @Override
   public Mino getExplorationMove(final GameView game)
   {
       int randIdx = this.getRandom().nextInt(game.getFinalMinoPositions().size());
       return game.getFinalMinoPositions().get(randIdx);
   }

   /**
    * This method is called by the TrainerAgent after we have played enough training games.
    * In between the training section and the evaluation section of a cycle, we need to use
    * the exprience we've collected (from the training games) to improve the q-function.
    *
    * You don't really need to change this method unless you want to. All that happens
    * is that we will use the experiences currently stored in the replay buffer to update
    * our model. Updates (i.e. gradient descent updates) will be applied per minibatch
    * (i.e. a subset of the entire dataset) rather than in a vanilla gradient descent manner
    * (i.e. all at once)...this often works better and is an active area of research.
    *
    * Each pass through the data is called an epoch, and we will perform "numUpdates" amount
    * of epochs in between the training and eval sections of each cycle.
    */
   @Override
   public void trainQFunction(Dataset dataset,
                              LossFunction lossFunction,
                              Optimizer optimizer,
                              long numUpdates)
   {
       for(int epochIdx = 0; epochIdx < numUpdates; ++epochIdx)
       {
           dataset.shuffle();
           Iterator<Pair<Matrix, Matrix> > batchIterator = dataset.iterator();

           while(batchIterator.hasNext())
           {
               Pair<Matrix, Matrix> batch = batchIterator.next();
               try
               {
                   Matrix YHat = this.getQFunction().forward(batch.getFirst());
                   optimizer.reset();
                   this.getQFunction().backwards(batch.getFirst(),
                                                 lossFunction.backwards(YHat, batch.getSecond()));
                   optimizer.step();
               } catch(Exception e)
               {
                   e.printStackTrace();
                   System.exit(-1);
               }
           }
       }
   }

   /**
    * This method is where you will devise your own reward signal. Remember, the larger
    * the number, the more "pleasurable" it is to the model, and the smaller the number,
    * the more "painful" to the model.
    *
    * This is where you get to tell the model how "good" or "bad" the game is.
    * Since you earn points in this game, the reward should probably be influenced by the
    * points, however this is not all. In fact, just using the points earned this turn
    * is a **terrible** reward function, because earning points is hard!!
    *
    * I would recommend you to consider other ways of measuring "good"ness and "bad"ness
    * of the game. For instance, the higher the stack of minos gets....generally the worse
    * (unless you have a long hole waiting for an I-block). When you design a reward
    * signal that is less sparse, you should see your model optimize this reward over time.
    */


    @Override
    public double getReward(final GameView game) {
        // Terminal state: the game has ended
        if (game.didAgentLose()) {
           // System.out.println("Terminal state reached.");
            return -100.0;  // Large penalty for losing
        }
    
        // Access the current board
        Board board = game.getBoard();

        int scoreThisTurn = game.getTotalScore();  // Sparse reward
        double scoreReward = 0.05 * scoreThisTurn;
    

        int maxHeight = 0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.getBlockAt(col, row) != null) {
                    int height = Board.NUM_ROWS - row;
                    maxHeight = Math.max(maxHeight, height);
                    break; // Move to next column
                }
            }
        }
       

        int holes = 0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            boolean blockFound = false;
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.getBlockAt(col, row) != null) {
                    blockFound = true;
                } else if (blockFound) {
                    holes++;  // Empty cell with block above it
                }
            }
        }
    

        int[] colHeights = new int[Board.NUM_COLS];
        for (int col = 0; col < Board.NUM_COLS; col++) {
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.getBlockAt(col, row) != null) {
                    colHeights[col] = Board.NUM_ROWS - row;
                    break;
                }
            }
        }
    
        double bumpiness = 0.0;
        for (int i = 0; i < Board.NUM_COLS - 1; i++) {
            bumpiness += Math.abs(colHeights[i] - colHeights[i + 1]);
        }
      
        int emptyRows = 0;
        int firstFilledRow = Board.NUM_ROWS - maxHeight;
        for (int row = firstFilledRow; row < Board.NUM_ROWS; row++) {
            boolean isEmpty = true;
            for (int col = 0; col < Board.NUM_COLS; col++) {
                if (board.getBlockAt(col, row) != null) {
                    isEmpty = false;
                    break;
                }
            }
            if (isEmpty) emptyRows++;
        }
        
        double maxHeightPenalty   = -1.5 * maxHeight;
        double holePenalty        = -3.0 * holes;
        double bumpinessPenalty   = -0.8 * bumpiness;
        double emptyRowReward     = 6.0 * emptyRows;
        double reward = scoreReward + maxHeightPenalty + holePenalty + bumpinessPenalty + emptyRowReward;        

        reward += 5.0;
    
        // Debug output
        // System.out.println("Reward Breakdown:");
        // System.out.println("Score: " + scoreThisTurn + "and" + scoreReward);
        // System.out.println("Max Height: " + maxHeight + "and " + maxHeightPenalty);
        // System.out.println("Holes: " + holes + " and " + holePenalty);
        // System.out.println("Bumpiness: " + bumpiness + " and " + bumpinessPenalty);
        // System.out.println("Empty Rows Under Max: " + emptyRows + "and" + emptyRowReward);
        // System.out.println("TOTAL REWARD: " + reward);
        
        return reward;
    }

   public double getBumpiness(final Matrix board) {
       double bumpiness = 0.0;
       int numCols = board.getShape().getNumCols();

       for (int col = 0; col < numCols - 1; col++) {
           int height1 = getColumnHeight(board, col);
           int height2 = getColumnHeight(board, col + 1);
           bumpiness += Math.abs(height1 - height2);
       }

       return bumpiness;
   }


   public int getNumEmptyRowsBelowMaxHeight(final Board board) {
    int numRows = Board.NUM_ROWS;
    int numCols = Board.NUM_COLS;

    // Step 1: Find the max height
    int maxHeight = 0;
    for (int col = 0; col < numCols; col++) {
        for (int row = 0; row < numRows; row++) {
            if (board.getBlockAt(col, row) != null) {
                int height = numRows - row;
                if (height > maxHeight) {
                    maxHeight = height;
                }
                break;
            }
        }
    }

    // Step 2: Count number of empty rows *under* max height
    int firstFilledRow = numRows - maxHeight;
    int emptyRows = 0;

    for (int row = firstFilledRow; row < numRows; row++) {
        boolean isEmpty = true;
        for (int col = 0; col < numCols; col++) {
            if (board.getBlockAt(col, row) != null) {
                isEmpty = false;
                break;
            }
        }
        if (isEmpty) {
            emptyRows++;
        }
    }

    return emptyRows;
}


   public int getColumnHeight(Matrix board, int col)
   {
       int numRows = board.getShape().getNumRows();
       for (int row = 0; row < numRows; row++) {
           if (board.get(row, col) != 0.0) {
               return numRows - row;
           }
       }
       return 0;
   }
 
   public double getMaxHeight(final Matrix board) {
       int numRows = board.getShape().getNumRows();
       int numCols = board.getShape().getNumCols();




       for (int row = 0; row < numRows; row++) {
           for (int col = 0; col < numCols; col++) {
               if (board.get(row, col) != 0.0) {
                   return numRows - row;
               }
           }
       }
       return 0.0;
   }

   public double getNumHoles(final Matrix board)
   {
       double numHoles = 0.0;
       
       int numRows = board.getShape().getNumRows();


       int numCols = board.getShape().getNumCols();
       for(int colIdx = 0; colIdx < numCols; ++colIdx)
       {
           // Check if the column is empty
           if(board.getCol(colIdx) == null)
           {
               continue;
           }




           // Count the number of holes in the column
           boolean holeFound = false;
           for(int rowIdx = 0; rowIdx < numRows; ++rowIdx)
           {
               if(board.get(rowIdx, colIdx) == 0.0)
               {
                   holeFound = true;
               }
               else if(holeFound)
               {
                   numHoles += 1.0;
               }
           }
       }
       return numHoles;
   }

   public double getNumRowsFilled(final Matrix board)
   {
       double numRowsFilled = 0.0;
       
       int numRows = board.getShape().getNumRows();


       int numCols = board.getShape().getNumCols();
       for (int row = 0; row < numRows; row++) {
           boolean isFilled = true;
           for (int col = 0; col < numCols; col++) {
               //check if the cell is filled
               if (board.get(row, col) == 0.0) {
                   isFilled = false;
                   break;
               }
           }
           if (isFilled) {
               numRowsFilled++;
           }
       }
       return numRowsFilled;
   }
   // 10. Lines cleared this turn
   public double getLinesClearedThisTurn(final Matrix board) {
       double linesCleared = 0.0;
       
       int numRows = board.getShape().getNumRows();


       int numCols = board.getShape().getNumCols();
       for (int row = 0; row < numRows; row++) {
           boolean isFull = true;
           for (int col = 0; col < numCols; col++) {
               if (board.get(row, col) == 0.0) {
                   isFull = false;
                   break;
               }
           }
           if (isFull) {
               linesCleared++;
           }
       }
       return linesCleared;
   }
 
   // 1. Aggregate height: sum of all column heights
public double getAggregateHeight(final Matrix board) {
   double totalHeight = 0.0;
   //int numRows = board.getShape().getNumRows();


   int numCols = board.getShape().getNumCols();
   for (int col = 0; col < numCols; col++) {
       totalHeight += getColumnHeight(board, col);
   }
   return totalHeight;
}

// 2. Average column height
public double getAverageHeight(final Matrix board) {
    //int numRows = board.getShape().getNumRows();


    int numCols = board.getShape().getNumCols();
   return getAggregateHeight(board) / numCols;
}

// 3. Standard deviation of column heights (a proxy for jaggedness)
public double getHeightStdDev(final Matrix board) {
   double avg = getAverageHeight(board);
   double sumSq = 0.0;
   int numRows = board.getShape().getNumRows();


   int numCols = board.getShape().getNumCols();
   for (int col = 0; col < numCols; col++) {
       double h = getColumnHeight(board, col);
       sumSq += (h - avg) * (h - avg);
   }
   return Math.sqrt(sumSq / numCols);
}

// 4. Well depth: deepest empty vertical well (i.e. tall columns on both sides)

public double getWellDepth(final Matrix board) {
   double maxWellDepth = 0.0;
   int numRows = board.getShape().getNumRows();

   int numCols = board.getShape().getNumCols();

   for (int col = 0; col < numCols; col++) {
       int left = (col > 0) ? getColumnHeight(board, col - 1) : numRows;
       int right = (col < Board.NUM_COLS - 1) ? getColumnHeight(board, col + 1) : numRows;
       int curr = getColumnHeight(board,col);
       if (curr < left && curr < right) {
           int wellDepth = Math.min(left, right) - curr;
           if (wellDepth > maxWellDepth) {
               maxWellDepth = wellDepth;
           }
       }
   }

   return maxWellDepth;
}

public double getBlocksAboveHoles(final Matrix board) {
   double count = 0.0;
   int numRows = board.getShape().getNumRows();
   int numCols = board.getShape().getNumCols();
   // Iterate through each column
   for (int col = 0; col < numCols; col++) {
       boolean holeFound = false;
       // Iterate through each row in the column
       for (int row = numRows - 1; row >= 0; row--)
        {
           if (board.get(row, col) == 0.0) {
               holeFound = true; // Found a hole
           } else if (holeFound) {
               count++;
           }
       }
   }
   return count;
}
}