package de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants;

import java.util.ArrayList;
import java.util.List;

import de.lmu.ifi.dbs.elki.data.spatial.SpatialComparable;
import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialDirectoryEntry;
import de.lmu.ifi.dbs.elki.index.tree.spatial.SpatialEntry;
import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.bulk.BulkSplit;
import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.util.InsertionStrategy;
import de.lmu.ifi.dbs.elki.persistent.PageFile;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;

/**
 * Abstract superclass for all non-flat R*-Tree variants.
 * 
 * @author Elke Achtert
 * 
 * @param <N> Node type
 * @param <E> Entry type
 */
public abstract class NonFlatRStarTree<N extends AbstractRStarTreeNode<N, E>, E extends SpatialEntry> extends AbstractRStarTree<N, E> {
  /**
   * Constructor.
   * 
   * @param pagefile Page file
   * @param bulkSplitter bulk load strategy
   * @param insertionStrategy the strategy to find the child to insert to
   */
  public NonFlatRStarTree(PageFile<N> pagefile, BulkSplit bulkSplitter, InsertionStrategy insertionStrategy) {
    super(pagefile, bulkSplitter, insertionStrategy);
  }

  /**
   * Returns true if in the specified node an overflow occurred, false
   * otherwise.
   * 
   * @param node the node to be tested for overflow
   * @return true if in the specified node an overflow occurred, false otherwise
   */
  @Override
  protected boolean hasOverflow(N node) {
    if(node.isLeaf()) {
      return node.getNumEntries() == leafCapacity;
    }
    else {
      return node.getNumEntries() == dirCapacity;
    }
  }

  /**
   * Returns true if in the specified node an underflow occurred, false
   * otherwise.
   * 
   * @param node the node to be tested for underflow
   * @return true if in the specified node an underflow occurred, false
   *         otherwise
   */
  @Override
  protected boolean hasUnderflow(N node) {
    if(node.isLeaf()) {
      return node.getNumEntries() < leafMinimum;
    }
    else {
      return node.getNumEntries() < dirMinimum;
    }
  }

  /**
   * Computes the height of this RTree. Is called by the constructor. and should
   * be overwritten by subclasses if necessary.
   * 
   * @return the height of this RTree
   */
  @Override
  protected int computeHeight() {
    N node = getRoot();
    int height = 1;

    // compute height
    while(!node.isLeaf() && node.getNumEntries() != 0) {
      E entry = node.getEntry(0);
      node = getNode(entry);
      height++;
    }
    return height;
  }

  @Override
  protected void createEmptyRoot(@SuppressWarnings("unused") E exampleLeaf) {
    N root = createNewLeafNode();
    writeNode(root);
    setHeight(1);
  }

  /**
   * Performs a bulk load on this RTree with the specified data. Is called by
   * the constructor and should be overwritten by subclasses if necessary.
   */
  @Override
  protected void bulkLoad(List<E> spatialObjects) {
    if(!initialized) {
      initialize(spatialObjects.get(0));
    }

    StringBuffer msg = getLogger().isDebuggingFine() ? new StringBuffer() : null;

    // Tiny tree that fit into a single page
    if(spatialObjects.size() <= leafCapacity) {
      N root = createNewLeafNode();
      root.setPageID(getRootID());
      writeNode(root);
      createRoot(root, spatialObjects);
      setHeight(1);
      if(msg != null) {
        msg.append("\n  numNodes = 1");
      }
    }
    // root is directory node
    else {
      N root = createNewDirectoryNode();
      root.setPageID(getRootID());
      writeNode(root);

      // create leaf nodes
      List<N> nodes = createBulkLeafNodes(spatialObjects);

      int numNodes = nodes.size();
      if(msg != null) {
        msg.append("\n  numLeafNodes = ").append(numNodes);
      }
      setHeight(1);

      // create directory nodes
      while(nodes.size() > (dirCapacity - 1)) {
        nodes = createDirectoryNodes(nodes);
        numNodes += nodes.size();
        setHeight(getHeight() + 1);
      }

      // create root
      createRoot(root, new ArrayList<N>(nodes));
      numNodes++;
      setHeight(getHeight() + 1);
      if(msg != null) {
        msg.append("\n  numNodes = ").append(numNodes);
      }
    }
    if(msg != null) {
      msg.append("\n  height = ").append(getHeight());
      msg.append("\n  root " + getRoot());
      getLogger().debugFine(msg.toString() + "\n");
    }
  }

  /**
   * Creates and returns the directory nodes for bulk load.
   * 
   * @param nodes the nodes to be inserted
   * @return the directory nodes containing the nodes
   */
  private List<N> createDirectoryNodes(List<N> nodes) {
    int minEntries = dirMinimum;
    int maxEntries = dirCapacity - 1;

    ArrayList<N> result = new ArrayList<N>();
    List<List<N>> partitions = bulkSplitter.partition(nodes, minEntries, maxEntries);

    for(List<N> partition : partitions) {
      // create node
      N dirNode = createNewDirectoryNode();
      writeNode(dirNode);
      result.add(dirNode);

      // insert nodes
      for(N o : partition) {
        dirNode.addDirectoryEntry(createNewDirectoryEntry(o));
      }

      // write to file
      writeNode(dirNode);
      if(getLogger().isDebuggingFiner()) {
        StringBuffer msg = new StringBuffer();
        msg.append("\npageNo ").append(dirNode.getPageID());
        getLogger().debugFiner(msg.toString() + "\n");
      }
    }

    return result;
  }

  /**
   * Returns a root node for bulk load. If the objects are data objects a leaf
   * node will be returned, if the objects are nodes a directory node will be
   * returned.
   * 
   * @param root the new root node
   * @param objects the spatial objects to be inserted
   * @return the root node
   */
  @SuppressWarnings("unchecked")
  private N createRoot(N root, List<? extends SpatialComparable> objects) {
    // insert data
    for(SpatialComparable object : objects) {
      if(object instanceof SpatialEntry) {
        E entry = (E) object;
        root.addLeafEntry(entry);
        throw new AbortException("Unexpected spatial comparable encountered.");
      }
      else {
        root.addDirectoryEntry(createNewDirectoryEntry((N) object));
      }
    }

    // set root mbr
    ((SpatialDirectoryEntry) getRootEntry()).setMBR(root.computeMBR());

    // write to file
    writeNode(root);
    if(getLogger().isDebuggingFiner()) {
      StringBuffer msg = new StringBuffer();
      msg.append("pageNo ").append(root.getPageID());
      getLogger().debugFiner(msg.toString() + "\n");
    }

    return root;
  }
}