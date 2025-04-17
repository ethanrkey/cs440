package src.labs.cp;


// SYSTEM IMPORTS
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


// JAVA PROJECT IMPORTS
import edu.bu.cp.linalg.Matrix;
import edu.bu.cp.nn.Model;
import edu.bu.cp.utils.Pair;


public class ReplayBuffer
    extends Object
{

    public static enum ReplacementType
    {
        RANDOM,
        OLDEST;
    }

    private ReplacementType     type;
    private int                 size;
    private int                 newestSampleIdx;

    private Matrix              prevStates;
    private Matrix              rewards;
    private Matrix              nextStates;
    private boolean             isStateTerminalMask[];

    private Random              rng;

    public ReplayBuffer(ReplacementType type,
                        int numSamples,
                        int dim,
                        Random rng)
    {
        this.type = type;
        this.size = 0;
        this.newestSampleIdx = -1;

        this.prevStates = Matrix.zeros(numSamples, dim);
        this.rewards = Matrix.zeros(numSamples, 1);
        this.nextStates = Matrix.zeros(numSamples, dim);
        this.isStateTerminalMask = new boolean[numSamples];

        this.rng = rng;

    }

    public int size() { return this.size; }
    public final ReplacementType getReplacementType() { return this.type; }
    private int getNewestSampleIdx() { return this.newestSampleIdx; }
    private Matrix getPrevStates() { return this.prevStates; }
    private Matrix getNextStates() { return this.nextStates; }
    private Matrix getRewards() { return this.rewards; }
    private boolean[] getIsStateTerminalMask() { return this.isStateTerminalMask; }

    private Random getRandom() { return this.rng; }

    private void setSize(int i) { this.size = i; }
    private void setNewestSampleIdx(int i) { this.newestSampleIdx = i; }

    private int chooseSampleToEvict()
    {
        int idxToEvict = -1;

        switch(this.getReplacementType())
        {
            case RANDOM:
                idxToEvict = this.getRandom().nextInt(this.getNextStates().getShape().getNumRows());
                break;
            case OLDEST:
                idxToEvict = (this.getNewestSampleIdx() + 1) % this.getNextStates().getShape().getNumRows();
                break;
            default:
                System.err.println("[ERROR] ReplayBuffer.chooseSampleToEvict: unknown replacement type "
                    + this.getReplacementType());
                System.exit(-1);
        }

        return idxToEvict;
    }

    public void addSample(Matrix prevState,
                      double reward,
                      Matrix nextState)
{
    int insertIdx;
    boolean hasSpace = this.size() < this.getNextStates().getShape().getNumRows();

    if (hasSpace) {
        insertIdx = this.size();
        this.setSize(this.size() + 1);
    } else {
        insertIdx = this.chooseSampleToEvict();
    }

    try {
        // Insert prevState
        this.getPrevStates().copySlice(insertIdx, insertIdx + 1, 0, prevState.getShape().getNumCols(), prevState);

        // Insert reward
        this.getRewards().set(insertIdx, 0, reward);

        // Insert nextState (if non-terminal)
        if (nextState != null) {
            this.getNextStates().copySlice(insertIdx, insertIdx + 1, 0, nextState.getShape().getNumCols(), nextState);
            this.getIsStateTerminalMask()[insertIdx] = false;
        } else {
            this.getIsStateTerminalMask()[insertIdx] = true;
        }

    } catch (Exception e) {
        System.err.println("[ERROR] addSample: Exception when copying slice");
        e.printStackTrace();
        System.exit(-1);
    }

    // Track latest sample if OLDEST replacement is used
    if (!hasSpace && this.getReplacementType() == ReplacementType.OLDEST) {
        this.setNewestSampleIdx(insertIdx);
    }
}


    public static double max(Matrix qValues) throws IndexOutOfBoundsException
    {
        double maxVal = 0;
        boolean initialized = false;

        for(int colIdx = 0; colIdx < qValues.getShape().getNumCols(); ++colIdx)
        {
            double qVal = qValues.get(0, colIdx);
            if(!initialized || qVal > maxVal)
            {
                maxVal = qVal;
            }
        }
        return maxVal;
    }


    public Matrix getGroundTruth(Model qFunction,
                             double discountFactor)
{
    Matrix Y = Matrix.zeros(this.size(), 1);

    for (int i = 0; i < this.size(); ++i)
    {
        double reward = this.getRewards().get(i, 0);

        if (this.getIsStateTerminalMask()[i]) {
            Y.set(i, 0, reward);
        } else {
            Matrix nextState = this.getNextStates().getRow(i);
            try {
                Matrix qValuesNext = qFunction.forward(nextState);
                double maxQ = max(qValuesNext);
                Y.set(i, 0, reward + discountFactor * maxQ);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    return Y;
}


    public Pair<Matrix, Matrix> getTrainingData(Model qFunction,
                                                double discountFactor)
    {
        Matrix X = Matrix.zeros(this.size(), this.getPrevStates().getShape().getNumCols());
        try
        {
            for(int rIdx = 0; rIdx < this.size(); ++rIdx)
            {
                X.copySlice(rIdx, rIdx+1, 0, X.getShape().getNumCols(),
                            this.getPrevStates().getRow(rIdx));
            }
        } catch(Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
        Matrix YGt = this.getGroundTruth(qFunction, discountFactor);

        return new Pair<Matrix, Matrix>(X, YGt);
    }

}

