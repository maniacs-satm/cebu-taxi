package org.openplans.cebutaxi.inference.impl;

import gov.sandia.cognition.math.matrix.Matrix;
import gov.sandia.cognition.math.matrix.MatrixFactory;
import gov.sandia.cognition.math.matrix.Vector;
import gov.sandia.cognition.math.matrix.VectorFactory;
import gov.sandia.cognition.math.signals.LinearDynamicalSystem;
import gov.sandia.cognition.statistics.bayesian.AbstractKalmanFilter;
import gov.sandia.cognition.statistics.distribution.MultivariateGaussian;

public class StandardTrackingFilter extends AbstractKalmanFilter {

  /**
   * Motion model of the underlying system.
   */
  protected LinearDynamicalSystem model;
  
  private final double gVariance;
  private final double aVariance;
  
  /*
   * Observation matrix
   */
  private static Matrix O;
  
  static {
    O = MatrixFactory.getDefault().createMatrix(2, 4);
    O.setElement(0, 0, 1);
    O.setElement(1, 2, 1);
  }
    
  /**
   * Standard 2D tracking model with the following state equation:
   * {@latex[ D_ x_t = G x_{t-1} + A \epsilon_t}
   * @param gVariance
   * @param aVariance
   */
  public StandardTrackingFilter(double gVariance,
      double aVariance) {
    
    super( VectorFactory.getDefault().createVector(4),
        createStateCovarianceMatrix(1d, aVariance),
        MatrixFactory.getDefault().createIdentity(2, 2).scale(gVariance) );
    
    this.aVariance = aVariance;
    this.gVariance = gVariance;
    
    LinearDynamicalSystem model = new LinearDynamicalSystem(0, 4);
    
    Matrix Gct = createStateTransitionMatrix(currentTimeDiff);
    Matrix G = MatrixFactory.getDefault().createIdentity(4, 4);
    G.setSubMatrix(0, 0, Gct);
    
    model.setA(G);
    model.setB(MatrixFactory.getDefault().createMatrix(4, 4));
    model.setC(O);
    
    this.model = model; 
  }

  private double currentTimeDiff = 1d;

  private double prevTimeDiff = 1d;

  public double getCurrentTimeDiff() {
    return currentTimeDiff;
  }

  public void setCurrentTimeDiff(double currentTimeDiff) {
    this.prevTimeDiff = this.currentTimeDiff;
    this.currentTimeDiff = currentTimeDiff;
  }

  private static Matrix createStateCovarianceMatrix(double timeDiff, double aVariance) {
    Matrix A_half = MatrixFactory.getDefault().createIdentity(4, 4);
    A_half.setElement(0, 0, Math.pow(timeDiff, 2)/2d);
    A_half.setElement(1, 1, timeDiff);
    A_half.setElement(2, 2, Math.pow(timeDiff, 2)/2d);
    A_half.setElement(3, 3, timeDiff);
    Matrix A = A_half.times(A_half.transpose());
    A.scaleEquals(aVariance);
    return A;
  }
  
  private static Matrix createStateTransitionMatrix(double timeDiff) {
    
    Matrix Gct = MatrixFactory.getDefault().createIdentity(4, 4);
    Gct.setElement(0, 1, timeDiff);
    Gct.setElement(2, 3, timeDiff);
    
    return Gct;
  }
  
  
  @Override
  public MultivariateGaussian createInitialLearnedObject() {
    return new MultivariateGaussian(
        this.model.getState(), this.getModelCovariance() );
  }

  @Override
  public void predict(MultivariateGaussian belief) {
    /*
     * From KalmanFilter.class
     */
    // Load the belief into the model and then predict the next state
    this.model.evaluate( this.currentInput, belief.getMean() );
    Vector xpred = this.model.getState();

    // Calculate the covariance, which will increase due to the
    // inherent uncertainty of the model.
    if (currentTimeDiff != prevTimeDiff) {
      Matrix modelCovariance = createStateCovarianceMatrix(currentTimeDiff, aVariance);
      this.setModelCovariance(modelCovariance);
      Matrix G = createStateTransitionMatrix(currentTimeDiff);
      this.model.setA(G);
    }
    Matrix P = this.computePredictionCovariance(
        this.model.getA(), belief.getCovariance() );

    // Load the updated belief
    belief.setMean( xpred );
    belief.setCovariance( P );
    
  }

  @Override
  public void measure(MultivariateGaussian belief, Vector observation) {
    
    final Matrix C = this.model.getC();

    // Figure out what the model says the observation should be
    Vector xpred = belief.getMean();
    Vector ypred = C.times( xpred );

    // Update step... compute the difference between the observation
    // and what the model says.
    // Then compute the Kalman gain, which essentially indicates
    // how much to believe the observation, and how much to believe model
    Vector innovation = observation.minus( ypred );
    this.computeMeasurementBelief(belief, innovation, C);
    
  }

  public static Matrix getObservationMatrix() {
    return O;
  }

}
