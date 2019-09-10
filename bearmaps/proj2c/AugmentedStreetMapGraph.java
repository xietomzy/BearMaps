package bearmaps.proj2c;

import bearmaps.hw4.streetmap.Node;
import bearmaps.hw4.streetmap.StreetMapGraph;
import bearmaps.proj2ab.Point;
import bearmaps.proj2ab.WeirdPointSet;

import java.util.*;
/** Aaron Louis Benedict Putterman made the suggestion to import
 * this class on the CS61B SP18 piazza post @4694
 */

/**
 * An augmented graph that is more powerful that a standard StreetMapGraph.
 * Specifically, it supports the following additional operations:
 *
 *
 * @author Alan Yao, Josh Hug, Tom Xie
 */
public class AugmentedStreetMapGraph extends StreetMapGraph {
    private WeirdPointSet kdTree;
    private List<Node> nodes;
    private Map<Point, Node> nodeToPointMap;
    private List<Point> points;
    private Trie trie;
    private HashMap<String, ArrayList<String>> cleanToFull;
    private HashMap<String, ArrayList<Node>> cleanToNode;
    public AugmentedStreetMapGraph(String dbPath) {
        super(dbPath);
        // You might find it helpful to uncomment the line below:
        nodes = this.getNodes();
        nodeToPointMap = new HashMap<>();
        for (Node n : nodes) {
            if (neighbors(n.id()).size() != 0) {
                double x = n.lon();
                double y = n.lat();
                Point pt = new Point(x, y);
                nodeToPointMap.put(pt, n);
            }
        }
        points = new ArrayList<>(nodeToPointMap.keySet());
        kdTree = new WeirdPointSet(points);
        trie = new Trie();
        cleanToFull = new HashMap<>();
        cleanToNode = new HashMap<>();
        for (Node n : nodes) {
            if (n.name() != null) {
                String name = n.name();
                String clean = cleanString(name);
                trie.put(clean, 0);
                if (!cleanToFull.containsKey(clean)) {
                    cleanToFull.put(clean, new ArrayList<>());
                }
                cleanToFull.get(clean).add(name);

                if (!cleanToNode.containsKey(clean)) {
                    cleanToNode.put(clean, new ArrayList<>());
                }
                cleanToNode.get(clean).add(n);
            }
        }
    }


    /**
     * For Project Part II
     * Returns the vertex closest to the given longitude and latitude.
     * @param lon The target longitude.
     * @param lat The target latitude.
     * @return The id of the node in the graph closest to the target.
     */
    public long closest(double lon, double lat) {
        Point closestPoint = kdTree.nearest(lon, lat);
        return nodeToPointMap.get(closestPoint).id();
    }


    /**
     * For Project Part III (gold points)
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public List<String> getLocationsByPrefix(String prefix) {
        String clean = cleanString(prefix);
        Iterable<String> cleanResults = trie.keysWithPrefix(clean);
        ArrayList<String> results = new ArrayList<>();
        for (String s : cleanResults) {
            for (String full : cleanToFull.get(s)) {
                results.add(full);
            }
        }
        return results;
    }

    /**
     * For Project Part III (gold points)
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */
    public List<Map<String, Object>> getLocations(String locationName) {
        /*List<String> locationNames = getLocationsByPrefix(cleanString(locationName));
        ArrayList<Map<String, Object>> results = new ArrayList<>();
        for (String n : locationNames) {
            for (Node node : cleanToNode.get(n)) {
                HashMap<String, Object> map = new HashMap<>();
                map.put(n, node);
                results.add(map);
            }
        }*/
        ArrayList<Map<String, Object>> results = new ArrayList<>();
        for (Node node : cleanToNode.get(cleanString(locationName))) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("lat", node.lat());
            map.put("lon", node.lon());
            map.put("name", node.name());
            map.put("id", node.id());
            results.add(map);
        }
        return results;
    }


    /**
     * Useful for Part III. Do not modify.
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    private static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

}
