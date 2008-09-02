package de.lmu.ifi.dbs.elki.algorithm;

import java.util.Iterator;

import de.lmu.ifi.dbs.elki.algorithm.result.Result;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.distance.DoubleDistance;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.EuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.utilities.Description;

/**
 * Dummy Algorithm, which just iterates over all points once, doing a 10NN query each.
 * Useful in testing e.g. index structures.
 * 
 * @author Erich Schubert
 */
public class DummyAlgorithm<V extends NumberVector<V,?>> extends AbstractAlgorithm<V> {

  /**
   * Empty constructor. Nothing to do.
   */
  public DummyAlgorithm() {
    super();
  }

  /**
   * Iterates over all points in the database.
   */
  protected void runInTime(Database<V> database) throws IllegalStateException {
    DistanceFunction<V,DoubleDistance> distFunc = new EuclideanDistanceFunction<V>();
    for(Iterator<Integer> iter = database.iterator(); iter.hasNext(); ) {
      Integer id = iter.next();
      database.get(id);
      // run a 10NN query for each point.
      database.kNNQueryForID(id, 10, distFunc);
    }
  }

  public Description getDescription() {
    return new Description("Dummy","Dummy Algorithm",
        "Iterates once over all points in the database. Useful for unit tests.","");
  }

  public Result<V> getResult() {
    return null;
  }
}
