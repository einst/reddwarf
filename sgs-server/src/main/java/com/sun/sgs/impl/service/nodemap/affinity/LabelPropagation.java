/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
//import com.sun.sgs.impl.service.nodemap.affinity.LabelPropagation.LabelNode;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.UndirectedSparseMultigraph;
import edu.uci.ics.jung.graph.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Initial implementation of label propagation algorithm for a single node.
 * <p>
 * An implementation of the algorithm presented in
 * "Near linear time algorithm to detect community structures in large-scale
 * networks" by U.N. Raghavan, R. Albert and S. Kumara 2007
 * <p>
 * Set logging to Level.FINEST for a trace of the algorithm (very verbose
 * and slow).
 * Set logging to Level.FINER to see the final labeled graph.
 */
public class LabelPropagation {
    private static final String PKG_NAME = 
            "com.sun.sgs.impl.service.nodemap.affinity";
    // Our logger
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PKG_NAME));

    // The producer of our graphs.
    private final GraphBuilder builder;

    // If true, gather statistics for each run.
    private final boolean gatherStats;
    // Statistics for the last run, only if gatherStats is true
    private Collection<AffinityGroup> groups;
    private long time;
    private int iterations;
    private double modularity;

    /**
     * Constructs a new instance of the label propagation algorithm.
     * @param builder the graph producer
     * @param gatherStats if {@code true}, gather extra statistics for each run.
     *            Useful for testing.
     */
    public LabelPropagation(GraphBuilder builder, boolean gatherStats) {
        this.builder = builder;
        this.gatherStats = gatherStats;
    }

    //JANE not sure of how best to return the groups
    /**
     * Find the communities, using a graph obtained from the graph builder
     * provided at construction time.  The communities are found using the
     * label propagation algorithm.
     *
     * @return the affinity groups
     */
    public Collection<AffinityGroup> findCommunities() {
        long startTime = System.currentTimeMillis();
        // Step 1.  Initialize all nodes in the network.
        //          Their labels are their Identities.
        Graph<LabelNode, WeightedEdge> graph = createLabelGraph();

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST,
                       "Graph creation took {0} milliseconds",
                       (System.currentTimeMillis() - startTime));
        }

        // Step 2.  Set t = 1;
        int t = 1;

        List<LabelNode> vertices =
                new ArrayList<LabelNode>(graph.getVertices());
        while (true) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "GRAPH at iteration {0} is {1}",
                                          t, graph);
            }
            // Step 3.  Arrange the nodes in a random order and set it to X.
            // Step 4.  For each vertices in X chosen in that specific order, 
            //          let the label of vertices be the label of the highest
            //          frequency of its neighbors.
            boolean changed = false;

            // Choose a different ordering for each iteration
            if (t > 1) {
                Collections.shuffle(vertices);
            }

            for (LabelNode vertex : vertices) {
                changed = setMostFrequentLabel(vertex, graph) || changed;
            }

            // Step 5. If every node has a label that the maximum number of
            //         their neighbors have, then stop.   Otherwise, set
            //         t++ and loop.
            // Note that Leung's paper suggests we don't need the extra stopping
            // condition if we include each node in the neighbor freq calc.
            if (!changed) {
                break;
            }
            t++;

            if (logger.isLoggable(Level.FINEST)) {
                // Log the affinity groups so far:
                Collection<AffinityGroup> intermediateGroups =
                        gatherGroups(graph);
                for (AffinityGroup group : intermediateGroups) {
                    StringBuffer logSB = new StringBuffer();
                    for (Identity id : group.getIdentities()) {
                        logSB.append(id + " ");
                    }
                    logger.log(Level.FINEST,
                               "Intermediate group {0} , members: {1}",
                               group, logSB.toString());
                }

            }
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "FINAL GRAPH IS {0}", graph);
        }
        groups = gatherGroups(graph);

        if (gatherStats) {
            // Record our statistics for this run, used for testing.
            time = System.currentTimeMillis() - startTime;
            iterations = t;
            modularity = calcModularity(builder.getAffinityGraph(), groups);
            printStats();
        }

        return groups;
    }

    /**
     * Obtains a graph from the graph builder, and converts it into a
     * graph that adds labels to the vertices.  This graph is the first
     * step in our label propagation algorithm (give each graph node a
     * unique label).
     *
     * @return a graph to run the label propagation algorithm over
     */
    private Graph<LabelNode, WeightedEdge> createLabelGraph() {
        Graph<LabelNode, WeightedEdge> graph =
                new UndirectedSparseMultigraph<LabelNode, WeightedEdge>();
        Graph<Identity, WeightedEdge> affinityGraph =
                builder.getAffinityGraph();

        Map<Identity, LabelNode> nodeMap = new HashMap<Identity, LabelNode>();
        for (Identity id : affinityGraph.getVertices()) {
            LabelNode newNode = new LabelNode(id);
            graph.addVertex(newNode);
            nodeMap.put(id, newNode);
        }
        for (WeightedEdge e : affinityGraph.getEdges()) {
            Pair<Identity> endpoints = affinityGraph.getEndpoints(e);
            graph.addEdge(new WeightedEdge(e.getWeight()),
                                          nodeMap.get(endpoints.getFirst()),
                                          nodeMap.get(endpoints.getSecond()));
        }

        return graph;

    }

    /**
     * Sets the label of {@code node} to the label used most frequently
     * by {@code node}'s neighbors.  Returns {@code true} if {@code node}'s
     * label changed.
     *
     * @param node a vertex
     * @param graph the full graph
     * @return {@code true} if {@code node}'s label is changed, {@code false}
     *        if it is not changed
     */
    private boolean setMostFrequentLabel(LabelNode node,
            Graph<LabelNode, WeightedEdge> graph)
    {
        Set<Integer> highestSet = getNeighborCounts(node, graph);

        // If our current label is in the set of highest labels, we're done.
        if (highestSet.contains(node.label)) {
            return false;
        }

        // Otherwise, choose a label at random
        List<Integer> random = new ArrayList<Integer>(highestSet);
        Collections.shuffle(random);
        node.label = random.get(0);
        logger.log(Level.FINEST, "Returning true: node is now {0}", node);
        return true;
    }

    /**
     * Given a graph, and a node within that graph, find the set of labels
     * with the highest count amongst {@code node}'s neighbors
     *
     * @param node the node whose neighbors labels will be examined
     * @param graph the entire graph
     * @return
     */
    private Set<Integer> getNeighborCounts(LabelNode node,
            Graph<LabelNode, WeightedEdge> graph)
    {
        // A map of labels -> count, effectively counting how many
        // of our neighbors use a particular label.
        Map<Integer, Long> labelMap = new HashMap<Integer, Long>();

        // Put our neighbors node into the map.  We allow parallel edges, and
        // use edge weights.
        // NOTE can remove some code if we decide we don't need parallel edges
        StringBuffer logSB = new StringBuffer();
        for (LabelNode neighbor : graph.getNeighbors(node)) {
            if (logger.isLoggable(Level.FINEST)) {
                logSB.append(neighbor + " ");
            }
            Integer label = neighbor.label;
            long value = labelMap.containsKey(label) ?
                         labelMap.get(label) : 0;
            Collection<WeightedEdge> edges = graph.findEdgeSet(node, neighbor);
            for (WeightedEdge edge : edges) {
                value = value + edge.getWeight();
            }
            labelMap.put(label, value);
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Neighbors of {0} : {1}",
                       node, logSB.toString());
        }

        // Now go through the labelMap, and create a map of
        // values to sets of labels.
        SortedMap<Long, Set<Integer>> countMap =
                new TreeMap<Long, Set<Integer>>();
        for (Map.Entry<Integer, Long> entry : labelMap.entrySet()) {
            long count = entry.getValue();
            Set<Integer> identSet = countMap.get(count);
            if (identSet == null) {
                identSet = new HashSet<Integer>();
            }
            identSet.add(entry.getKey());
            countMap.put(count, identSet);
        }
        // Return the set of labels with the highest count.
        return countMap.get(countMap.lastKey());
    }

    /**
     * Return the affinity groups found within the graph putting all
     * nodes with the same label in a group.  The affinity group's id
     * will be the label.
     *
     * @param graph the input graph
     * @return the affinity groups
     */
    private Collection<AffinityGroup> gatherGroups (
            Graph<LabelNode, WeightedEdge> graph)
    {
        // All nodes with the same label are in the same community.
        Map<Integer, AffinityGroup> groupMap =
                new HashMap<Integer, AffinityGroup>();
        for (LabelNode vertex : graph.getVertices()) {
            AffinityGroupImpl ag =
                    (AffinityGroupImpl) groupMap.get(vertex.label);
            if (ag == null) {
                ag = new AffinityGroupImpl(vertex.label);
                groupMap.put(vertex.label, ag);
            }
            ag.addIdentity(vertex.id);
        }
        return groupMap.values();
    }

    public long getTime()         { return time; }
    public int getIterations()    { return iterations; }
    public double getModularity() { return modularity; }

    /**
     * Print statistics about this run;  useful for testing.
     */
    private void printStats() {
        System.out.print(" LPA took " +
            time + " milliseconds, " + iterations + " iterations, and found " +
            groups.size() + " groups ");
        System.out.printf(" LPA modularity %.4f %n", modularity);
        for (AffinityGroup group : groups) {
            System.out.print(" id: " + group.getId() + ": members: ");
            for (Identity id : group.getIdentities()) {
                System.out.print(id + " ");
            }
            System.out.println(" ");
        }
    }
    
    // This doesn't belong here, it doesn't apply to any particular
    // algorithm for finding affinity groups.
    /**
     * Given a graph and a set of partitions of it, calculate the modularity.
     * Modularity is a quality measure for the goodness of a clustering
     * algorithm, and is, essentially, the number of edges within communities
     * subtracted by the expected number of such edges.
     * <p>
     * The modularity will be a number between 0.0 and 1.0, with a higher
     * number being better.
     * <p>
     * See "Finding community structure in networks using eigenvectors
     * of matrices" 2006 Mark Newman.
     *
     * @param graph the graph which was devided into communities
     * @param groups the communities found in the graph
     * @return
     */
    public static double calcModularity(Graph<Identity, WeightedEdge> graph,
            Collection<AffinityGroup> groups)
    {
        final int m = graph.getEdgeCount();
        final int doublem = 2 * graph.getEdgeCount();

        // For each pair of vertices that are in the same community,
        // compute 1/(2m) * Sum(Aij - Pij), where Pij is kikj/2m.
        // See equation (18) in Newman's paper.
        // Note also that modularity can be expressed as
        // Sum(eii - ai*ai) where eii is the fraction of edges in the group
        // and ai*ai is the expected fraction of edges in the group.
        // See Raghavan paper (JANE also other earlier refs?)
        double q = 0;

        for (AffinityGroup g : groups) {  
            // value is weighted edge count within the community           
            long value = 0;
            // totEdges is the total number of connections for this community
            long totEdges = 0;
            
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(g.getIdentities());
            for (Identity id : groupList) {
                for (WeightedEdge edge : graph.getIncidentEdges(id)) {
                    totEdges = totEdges + edge.getWeight();
                }
            }
            // Look at each of the pairs in the community to find the number
            // of edges within
            while (!groupList.isEmpty()) {
                // Get the first identity
                Identity v1 = groupList.remove(0);
                for (Identity v2 : groupList) {
                    Collection<WeightedEdge> edges = graph.findEdgeSet(v1, v2);
                    // Calculate the adjacency info for v1 and v2
                    // We allow parallel, weighted edges
                    for (WeightedEdge edge : edges) {
                        value = value + (edge.getWeight() * 2);
                    }
                }
            }

            double tmp = (double) totEdges / doublem;
            q = q + (((double) value / doublem) - (tmp * tmp));
        }
        return q;
    }

    /**
     * Calculates Jaccard's index for a pair of affinity groups, which is
     * a measurement of similarity.  The value will be between {@code 0.0}
     * and {@code 1.0}, with higher values indicating stronger similarity
     * between two samples.
     *
     * @param sample1 the first sample
     * @param sample2 the second sample
     * @return the Jaccard index, a value between {@code 0.0} and {@code 1.0},
     *    with higer values indicating more similarity
     */
    public static double calcJaccard(Collection<AffinityGroup> sample1,
                                     Collection<AffinityGroup> sample2)
    {
        // a is number of pairs of identities in same affinity group
        //    in both samples
        // b is number of pairs that are in the same affinity gruop
        //    in the first sample only
        // c is the number of pairs that in the same affinity group
        //    in the second sample only
        long a = 0;
        long b = 0;
        long c = 0;
        for (AffinityGroup group : sample1) {
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(group.getIdentities());
            while (!groupList.isEmpty()) {
                Identity v1 = groupList.remove(0);
                for (Identity v2: groupList) {
                    // v1 and v2 are in the same group in sample1.  Are they
                    // in the same group in sample2?
                    if (inSameGroup(v1, v2, sample2)) {
                        a++;
                    } else {
                        b++;
                    }
                }
            }
        }
        for (AffinityGroup group : sample2) {
            ArrayList<Identity> groupList =
                    new ArrayList<Identity>(group.getIdentities());
            while (!groupList.isEmpty()) {
                Identity v1 = groupList.remove(0);
                for (Identity v2: groupList) {
                    // v1 and v2 are in the same group in sample2.  Count those
                    // that are not in the same group in sample1.
                    if (!inSameGroup(v1, v2, sample1)) {
                        c++;
                    }
                }
            }
        }

        // Jaccard's index (or coefficient) is defined as a/(a+b+c).
        return ((double) a / (double) (a + b + c));
    }

    private static boolean inSameGroup(Identity id1, Identity id2,
                                       Collection<AffinityGroup> group)
    {
        for (AffinityGroup g : group) {
            Set<Identity> idents = g.getIdentities();
            if (idents.contains(id1) && idents.contains(id2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vertices for our LPA graph.  Labels change as we iterate
     * through the algorithm, and once it has converged, vertices
     * with the same label are in the same cluster.
     * <p>
     * We are using the identity's hash code for the label for faster
     * comparisions.  This has some risk of us clustering identities
     * that actually are not related, because hash codes are not guaranteed
     * to be unique.
     */
    private static class LabelNode {
        final Identity id;
        int label;

        public LabelNode(Identity id) {
            this.id = id;
            label = id.hashCode();
        }

        public String toString() {
            return "[" + id.toString() + ":" + label + "]";
        }
    }
}
