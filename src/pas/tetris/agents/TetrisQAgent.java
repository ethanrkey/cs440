package src.pas.tetris.agents;

import java.util.ArrayList;
// SYSTEM IMPORTS
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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
import edu.bu.pas.tetris.nn.layers.Dense; 
import edu.bu.pas.tetris.nn.layers.ReLU; 
import edu.bu.pas.tetris.nn.layers.Tanh;
import edu.bu.pas.tetris.nn.layers.Sigmoid;
import edu.bu.pas.tetris.training.data.Dataset;
import edu.bu.pas.tetris.utils.Pair;

public class TetrisQAgent extends QAgent {

    private int previousFullRows = 0;
    public static final double EPSILON_CONSTANT = 0.05;
    private Random rng;

    public TetrisQAgent(String name) {
        super(name);
        this.rng = new Random(12345);
    }

    public Random getRandom() {
        return this.rng;
    }

    @Override
    public Model initQFunction() {
        final int inputDim  = 5;
        final int hidden1   = 8;
        final int hidden2   = 2;
        final int outputDim = 1;

        Sequential qFunction = new Sequential();

        qFunction.add(new Dense(inputDim, hidden1));
        qFunction.add(new ReLU());

        qFunction.add(new Dense(hidden1, hidden2));
        qFunction.add(new ReLU());

        qFunction.add(new Dense(hidden2, outputDim));

        return qFunction;
    }

    @Override
    public Matrix getQFunctionInput(final GameView game, final Mino potentialAction) {
        try {
            Matrix simBoard       = game.getGrayscaleImage(potentialAction);
            double numRowsCleared = 0.0;

            for (int row = 0; row < Board.NUM_ROWS; row++) {
                boolean isFull = true;
                for (int col = 0; col < Board.NUM_COLS; col++) {
                    double val = simBoard.get(row, col);
                    if (val == 0.0) {
                        isFull = false;
                        break;
                    }
                }
                if (isFull) {
                    numRowsCleared++;
                }
            }

            List<Double> features = new ArrayList<>();
            features.add(getNumHoles(simBoard));
            features.add(numRowsCleared);
            features.add(getBumpiness(simBoard));
            features.add(getAverageHeight(simBoard));
            features.add((double) game.getScoreThisTurn());

            int totalCols = features.size();
            Matrix combined = Matrix.zeros(1, totalCols);
            for (int i = 0; i < features.size(); i++) {
                combined.set(0, i, features.get(i));
            }
            return combined;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }

    @Override
    public boolean shouldExplore(final GameView game, final GameCounter gameCounter) {
        long cycleIdx = gameCounter.getCurrentCycleIdx();
        long gameIdx  = gameCounter.getCurrentGameIdx();

        double cycleDecayRate = 0.05;
        double gameDecayRate  = 0.05;
        double cycleEps       = Math.exp(-cycleDecayRate * cycleIdx);
        double epsilon        = Math.pow(cycleEps, gameDecayRate * gameIdx);

        return getRandom().nextDouble() <= epsilon;
    }

    @Override
    public Mino getExplorationMove(final GameView game) {
        int randIdx = this.getRandom().nextInt(game.getFinalMinoPositions().size());
        return game.getFinalMinoPositions().get(randIdx);
    }

    @Override
    public void trainQFunction(Dataset dataset,
                               LossFunction lossFunction,
                               Optimizer optimizer,
                               long numUpdates) {
        for (int epochIdx = 0; epochIdx < numUpdates; ++epochIdx) {
            dataset.shuffle();
            Iterator<Pair<Matrix, Matrix>> batchIterator = dataset.iterator();
            while (batchIterator.hasNext()) {
                Pair<Matrix, Matrix> batch = batchIterator.next();
                try {
                    Matrix YHat = this.getQFunction().forward(batch.getFirst());
                    optimizer.reset();
                    this.getQFunction().backwards(batch.getFirst(),
                        lossFunction.backwards(YHat, batch.getSecond()));
                    optimizer.step();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }

    public double getAverageHeight(final Board board) {
        double totalHeight = 0.0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            totalHeight += getColumnHeight(board, col);
        }
        return totalHeight / Board.NUM_COLS;
    }

    public int getColumnHeight(final Board board, int col) {
        for (int row = 0; row < Board.NUM_ROWS; row++) {
            if (board.getBlockAt(col, row) != null) {
                return Board.NUM_ROWS - row;
            }
        }
        return 0;
    }

    @Override
    public double getReward(final GameView game) {
        Board board = game.getBoard();
        int averageHeight = 0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            averageHeight += getColumnHeight(board, col);
        }
        averageHeight /= Board.NUM_COLS;

        int holes = 0;
        for (int col = 0; col < Board.NUM_COLS; col++) {
            boolean blockFound = false;
            for (int row = 0; row < Board.NUM_ROWS; row++) {
                if (board.getBlockAt(col, row) != null) {
                    blockFound = true;
                } else if (blockFound) {
                    holes++;
                }
            }
        }

        int rowsCleared = 0;
        for (int row = 0; row < Board.NUM_ROWS; row++) {
            boolean isFull = true;
            for (int col = 0; col < Board.NUM_COLS; col++) {
                if (board.isCoordinateOccupied(col, row)) {
                    isFull = false;
                    break;
                }
            }
            if (isFull) {
                rowsCleared++;
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

        int scoreThisTurn   = game.getScoreThisTurn();
        double scoreReward  = 100.0 * scoreThisTurn;

        System.out.println("numRowsCleared " + rowsCleared);
        double averageHeightPenalty = -0.5 * averageHeight;
        double holePenalty          = -0.5 * holes;
        double bumpinessPenalty     = -0.5 * bumpiness;
        double linesClearedReward   = 5.0 * rowsCleared;
        double reward = scoreReward
                        + averageHeightPenalty
                        + holePenalty
                        + bumpinessPenalty
                        + linesClearedReward;
        System.out.println("Reward: " + reward);
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

    public int countEmptyRowsBelowMaxHeight(final Board board) {
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

        // Step 2: Count number of empty rows under max height
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

    public int getColumnHeight(Matrix board, int col) {
        int numRows = board.getShape().getNumRows();
        for (int row = 0; row < numRows; row++) {
            if (board.get(row, col) != 0.0) {
                return numRows - row;
            }
        }
        return 0;
    }

    public double getNumHoles(final Matrix board) {
        double numHoles = 0.0;
        int numRows = board.getShape().getNumRows();
        int numCols = board.getShape().getNumCols();

        for (int colIdx = 0; colIdx < numCols; ++colIdx) {
            if (board.getCol(colIdx) == null) {
                continue;
            }
            boolean holeFound = false;
            for (int rowIdx = 0; rowIdx < numRows; ++rowIdx) {
                if (board.get(rowIdx, colIdx) == 0.0) {
                    holeFound = true;
                } else if (holeFound) {
                    numHoles += 1.0;
                }
            }
        }
        return numHoles;
    }

    public double getNumRowsFilled(final Matrix board) {
        double numRowsFilled = 0.0;
        int numRows = board.getShape().getNumRows();
        int numCols = board.getShape().getNumCols();

        for (int row = 0; row < numRows; row++) {
            boolean isFilled = true;
            for (int col = 0; col < numCols; col++) {
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

    public double countLinesClearedThisTurn(final Matrix board) {
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

    public double getAggregateHeight(final Matrix board) {
        double totalHeight = 0.0;
        int numCols = board.getShape().getNumCols();
        for (int col = 0; col < numCols; col++) {
            totalHeight += getColumnHeight(board, col);
        }
        return totalHeight;
    }

    public double getAverageHeight(final Matrix board) {
        int numCols = board.getShape().getNumCols();
        return getAggregateHeight(board) / numCols;
    }

    public double getHeightStdDev(final Matrix board) {
        double avg   = getAverageHeight(board);
        double sumSq = 0.0;
        int numRows  = board.getShape().getNumRows();
        int numCols  = board.getShape().getNumCols();
        for (int col = 0; col < numCols; col++) {
            double h = getColumnHeight(board, col);
            sumSq += (h - avg) * (h - avg);
        }
        return Math.sqrt(sumSq / numCols);
    }

    public double getWellDepth(final Matrix board) {
        double maxWellDepth = 0.0;
        int numRows = board.getShape().getNumRows();
        int numCols = board.getShape().getNumCols();

        for (int col = 0; col < numCols; col++) {
            int left  = (col > 0) ? getColumnHeight(board, col - 1) : numRows;
            int right = (col < Board.NUM_COLS - 1) ? getColumnHeight(board, col + 1) : numRows;
            int curr  = getColumnHeight(board, col);
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
        double count   = 0.0;
        int numRows    = board.getShape().getNumRows();
        int numCols    = board.getShape().getNumCols();

        for (int col = 0; col < numCols; col++) {
            boolean holeFound = false;
            for (int row = numRows - 1; row >= 0; row--) {
                if (board.get(row, col) == 0.0) {
                    holeFound = true;
                } else if (holeFound) {
                    count++;
                }
            }
        }
        return count;
    }

}
