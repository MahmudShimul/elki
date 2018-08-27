/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2018
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.math.statistics.dependence;

import de.lmu.ifi.dbs.elki.utilities.datastructures.arraylike.NumberArrayAdapter;
import de.lmu.ifi.dbs.elki.utilities.datastructures.heap.DoubleMinHeap;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import net.jafama.FastMath;

/**
 * Compute the similarity of dimensions using the SURFING score. The parameter k
 * for the k nearest neighbors is currently hard-coded to 10% of the set size.
 * <p>
 * Note that the complexity is roughly O(n n k), so this is a rather slow
 * method, and with k at 10% of n, is actually cubic: O(0.1 * n²).
 * <p>
 * This version cannot use index support, as the API operates without database
 * attachment. However, it should be possible to implement some trivial
 * sorted-list indexes to get a reasonable speedup!
 * <p>
 * Reference:
 * <p>
 * Elke Achtert, Hans-Peter Kriegel, Erich Schubert, Arthur Zimek:<br>
 * Interactive Data Mining with 3D-Parallel-Coordinate-Trees.<br>
 * Proc. 2013 ACM Int. Conf. on Management of Data (SIGMOD 2013)
 * <p>
 * Based on:
 * <p>
 * Christian Baumgartner, Claudia Plant, Karin Kailing, Hans-Peter Kriegel, and
 * Peer Kröger<br>
 * Subspace Selection for Clustering High-Dimensional Data<br>
 * Proc. IEEE International Conference on Data Mining (ICDM 2004)
 *
 * TODO: make the subspace distance function and k parameterizable.
 *
 * @author Robert Rödler
 * @author Erich Schubert
 * @since 0.7.0
 */
@Reference(authors = "Elke Achtert, Hans-Peter Kriegel, Erich Schubert, Arthur Zimek", //
    title = "Interactive Data Mining with 3D-Parallel-Coordinate-Trees", //
    booktitle = "Proc. 2013 ACM Int. Conf. on Management of Data (SIGMOD 2013)", //
    url = "https://doi.org/10.1145/2463676.2463696", //
    bibkey = "DBLP:conf/sigmod/AchtertKSZ13")
@Reference(authors = "Christian Baumgartner, Claudia Plant, Karin Kailing, Hans-Peter Kriegel, and Peer Kröger", //
    title = "Subspace Selection for Clustering High-Dimensional Data", //
    booktitle = "Proc. IEEE International Conference on Data Mining (ICDM 2004)", //
    url = "https://doi.org/10.1109/ICDM.2004.10112", //
    bibkey = "DBLP:conf/icdm/BaumgartnerPKKK04")
public class SURFINGDependenceMeasure extends AbstractDependenceMeasure {
  /**
   * Static instance.
   */
  public static final SURFINGDependenceMeasure STATIC = new SURFINGDependenceMeasure();

  /**
   * Constructor. Use static instance instead!
   */
  protected SURFINGDependenceMeasure() {
    super();
  }

  @Override
  public <A, B> double dependence(NumberArrayAdapter<?, A> adapter1, A data1, NumberArrayAdapter<?, B> adapter2, B data2) {
    final int len = size(adapter1, data1, adapter2, data2);
    final int k = Math.max(1, len / 10);

    double[] knns = new double[len];

    DoubleMinHeap heap = new DoubleMinHeap(k);
    double kdistmean = 0.;
    for(int i = 0; i < len; ++i) {
      double ix = adapter1.getDouble(data1, i),
          iy = adapter2.getDouble(data2, i);
      heap.clear();
      for(int j = 0; j < len; ++j) {
        double jx = adapter1.getDouble(data1, j),
            jy = adapter2.getDouble(data2, j);
        double dx = ix - jx, dy = iy - jy;
        heap.add(dx * dx + dy * dy); // Squared Euclidean.
      }
      double kdist = FastMath.sqrt(heap.peek()); // Euclidean
      knns[i] = kdist;
      kdistmean += kdist;
    }
    kdistmean /= len;
    // Deviation from mean:
    double diff = 0.;
    int below = 0;
    for(int l = 0; l < knns.length; l++) {
      diff += Math.abs(kdistmean - knns[l]);
      if(knns[l] < kdistmean) {
        below++;
      }
    }
    return (below > 0) ? diff / (2. * kdistmean * below) : 0;
  }

  /**
   * Parameterization class.
   *
   * @author Erich Schubert
   *
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    @Override
    protected SURFINGDependenceMeasure makeInstance() {
      return STATIC;
    }
  }
}
