package dr.inference.operators.hmc;

import dr.evomodel.substmodel.ComplexColtEigenSystem;
import dr.evomodel.substmodel.EigenDecomposition;
import dr.inference.model.MatrixParameterInterface;
import dr.inference.model.Parameter;
import dr.math.matrixAlgebra.EJMLUtils;
import dr.math.matrixAlgebra.WrappedVector;
import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;


public class GeodesicLeapFrogEngine extends HamiltonianMonteCarloOperator.LeapFrogEngine.Default {

    private final MatrixParameterInterface matrixParameter;
    private final DenseMatrix64F positionMatrix;
    private final DenseMatrix64F innerProduct;
    private final DenseMatrix64F innerProduct2;
    private final DenseMatrix64F projection;
    private final DenseMatrix64F momentumMatrix;
    private final int nRows;
    private final int nCols;


    GeodesicLeapFrogEngine(Parameter parameter, HamiltonianMonteCarloOperator.InstabilityHandler instabilityHandler,
                           MassPreconditioner preconditioning, double[] mask) {
        super(parameter, instabilityHandler, preconditioning, mask);
        this.matrixParameter = (MatrixParameterInterface) parameter;
        this.nRows = matrixParameter.getRowDimension();
        this.nCols = matrixParameter.getColumnDimension();
        this.positionMatrix = new DenseMatrix64F(nCols, nRows);
        this.innerProduct = new DenseMatrix64F(nCols, nCols);
        this.innerProduct2 = new DenseMatrix64F(nCols, nCols);
        this.projection = new DenseMatrix64F(nCols, nRows);
        this.momentumMatrix = new DenseMatrix64F(nCols, nRows);
    }

    @Override
    public void updateMomentum(double[] position, double[] momentum, double[] gradient,
                               double functionalStepSize) throws HamiltonianMonteCarloOperator.NumericInstabilityException {
        super.updateMomentum(position, momentum, gradient, functionalStepSize);
        projectMomentum(momentum, position);

    }

    @Override
    public void updatePosition(double[] position, WrappedVector momentum,
                               double functionalStepSize) throws HamiltonianMonteCarloOperator.NumericInstabilityException {

        positionMatrix.setData(position);
        System.arraycopy(momentum.getBuffer(), momentum.getOffset(), momentumMatrix.data, 0, momentum.getDim());
        CommonOps.multTransB(positionMatrix, momentumMatrix, innerProduct);
        CommonOps.multTransB(momentumMatrix, momentumMatrix, innerProduct2);

        double[][] XtV = new double[nCols][nCols];
        double[][] VtV = new double[2 * nCols][2 * nCols];

        for (int i = 0; i < nCols; i++) {
            VtV[i + nCols][i] = 1;
            for (int j = 0; j < nCols; j++) {
                XtV[i][j] = innerProduct.get(j, i);
                VtV[i][j] = innerProduct.get(j, i);
                VtV[i + nCols][j + nCols] = innerProduct.get(j, i);
                VtV[i][j + nCols] = innerProduct2.get(j, i);
            }
        }

        double[] expBuffer = new double[nCols * nCols];


        ComplexColtEigenSystem eigSystem = new ComplexColtEigenSystem(nCols);
        EigenDecomposition eigDecomposition = eigSystem.decomposeMatrix(XtV);
        eigSystem.computeExponential(eigDecomposition, functionalStepSize, expBuffer);


        double[] expBuffer2 = new double[nCols * nCols * 4];

        ComplexColtEigenSystem eigSystem2 = new ComplexColtEigenSystem(nCols * 2);
        EigenDecomposition eigDecomposition2 = eigSystem2.decomposeMatrix(VtV);
        eigSystem2.computeExponential(eigDecomposition2, functionalStepSize, expBuffer2);

        DenseMatrix64F X = new DenseMatrix64F(nCols * 2, nCols * 2);
        DenseMatrix64F Y = new DenseMatrix64F(nCols * 2, nCols * 2);

        for (int i = 0; i < nCols; i++) {
            for (int j = 0; j < nCols; j++) {
                X.set(i, j, expBuffer[i * nCols + j]);
                X.set(i + nCols, j + nCols, expBuffer[i * nCols + j]);
            }
        }
        Y.setData(expBuffer2);

        DenseMatrix64F Z = new DenseMatrix64F(nCols * 2, nCols * 2);

        CommonOps.mult(Y, X, Z);

        DenseMatrix64F PM = new DenseMatrix64F(nCols * 2, nRows);
        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                PM.set(j, i, positionMatrix.get(j, i));
                PM.set(j + nCols, i, momentumMatrix.get(j, i));
            }
        }

        DenseMatrix64F W = new DenseMatrix64F(2 * nCols, nRows);
        CommonOps.mult(Z, PM, W);

        for (int i = 0; i < nRows; i++) {
            for (int j = 0; j < nCols; j++) {
                positionMatrix.set(j, i, W.get(j, i));
            }
        }

        System.arraycopy(positionMatrix.data, 0, position, 0, position.length);
        CommonOps.multTransB(positionMatrix, positionMatrix, innerProduct);
        System.out.println(innerProduct);


    }

    @Override
    public void projectMomentum(double[] momentum, double[] position) {
        positionMatrix.setData(position);
        momentumMatrix.setData(momentum);

        CommonOps.multTransB(positionMatrix, momentumMatrix, innerProduct);
        EJMLUtils.addWithTransposed(innerProduct);

        CommonOps.mult(0.5, innerProduct, positionMatrix, projection);
        CommonOps.subtractEquals(momentumMatrix, projection);
    }
}
