package net.krona.politicsmod.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a set of chunks into map polygons (block coordinates): one outline per connected area,
 * with the unclaimed gaps inside it as holes. Used by the web map and minimap integrations.
 */
public final class ChunkOutline {

    /** A corner point in block coordinates. */
    public record Point(int x, int z) {
    }

    public record Polygon(List<Point> outer, List<List<Point>> holes) {
    }

    private record Edge(Point from, Point to) {
    }

    private ChunkOutline() {
    }

    /** chunks = chunk coordinates as {x, z}. */
    public static List<Polygon> trace(Collection<int[]> chunks) {
        Set<Long> cells = new HashSet<>();
        for (int[] c : chunks) cells.add(key(c[0], c[1]));

        // Boundary edges, clockwise around every cell (x to the east, z to the south)
        Map<Point, List<Edge>> byStart = new HashMap<>();
        for (int[] c : chunks) {
            int x0 = c[0] * 16, z0 = c[1] * 16, x1 = x0 + 16, z1 = z0 + 16;
            if (!cells.contains(key(c[0], c[1] - 1))) addEdge(byStart, new Point(x0, z0), new Point(x1, z0));
            if (!cells.contains(key(c[0] + 1, c[1]))) addEdge(byStart, new Point(x1, z0), new Point(x1, z1));
            if (!cells.contains(key(c[0], c[1] + 1))) addEdge(byStart, new Point(x1, z1), new Point(x0, z1));
            if (!cells.contains(key(c[0] - 1, c[1]))) addEdge(byStart, new Point(x0, z1), new Point(x0, z0));
        }

        List<List<Point>> outers = new ArrayList<>();
        List<List<Point>> holes = new ArrayList<>();
        for (List<Edge> edges : new ArrayList<>(byStart.values())) {
            while (!edges.isEmpty()) {
                List<Point> loop = walk(byStart, edges.get(0));
                if (loop.size() < 3) continue;
                (area(loop) > 0 ? outers : holes).add(loop);
            }
        }

        List<Polygon> result = new ArrayList<>();
        for (List<Point> outer : outers) result.add(new Polygon(outer, new ArrayList<>()));
        for (List<Point> hole : holes) {
            for (Polygon p : result) {
                if (contains(p.outer(), probe(hole))) {
                    p.holes().add(hole);
                    break;
                }
            }
        }
        return result;
    }

    private static void addEdge(Map<Point, List<Edge>> byStart, Point from, Point to) {
        byStart.computeIfAbsent(from, k -> new ArrayList<>()).add(new Edge(from, to));
    }

    /** Follows edges until the loop closes; at pinch points it turns right to keep loops simple. */
    private static List<Point> walk(Map<Point, List<Edge>> byStart, Edge first) {
        List<Point> loop = new ArrayList<>();
        Edge edge = first;
        byStart.get(first.from()).remove(first);
        while (true) {
            loop.add(edge.from());
            if (edge.to().equals(first.from())) break;
            List<Edge> next = byStart.get(edge.to());
            if (next == null || next.isEmpty()) break;
            Edge chosen = next.get(0);
            if (next.size() > 1) {
                int dx = edge.to().x() - edge.from().x(), dz = edge.to().z() - edge.from().z();
                for (Edge candidate : next) {
                    int cx = candidate.to().x() - candidate.from().x(), cz = candidate.to().z() - candidate.from().z();
                    if ((long) dx * cz - (long) dz * cx > 0) chosen = candidate; // right turn
                }
            }
            next.remove(chosen);
            edge = chosen;
        }
        return simplify(loop);
    }

    /** Drops points in the middle of straight runs. */
    private static List<Point> simplify(List<Point> loop) {
        List<Point> out = new ArrayList<>();
        int n = loop.size();
        for (int i = 0; i < n; i++) {
            Point prev = loop.get((i + n - 1) % n), cur = loop.get(i), next = loop.get((i + 1) % n);
            long cross = (long) (cur.x() - prev.x()) * (next.z() - cur.z()) - (long) (cur.z() - prev.z()) * (next.x() - cur.x());
            if (cross != 0) out.add(cur);
        }
        return out;
    }

    /** Shoelace area; positive for outlines, negative for holes. */
    private static long area(List<Point> loop) {
        long sum = 0;
        for (int i = 0; i < loop.size(); i++) {
            Point a = loop.get(i), b = loop.get((i + 1) % loop.size());
            sum += (long) a.x() * b.z() - (long) b.x() * a.z();
        }
        return sum;
    }

    /** A point just inside a hole outline's first vertical edge, safe for ray casting. */
    private static double[] probe(List<Point> hole) {
        for (int i = 0; i < hole.size(); i++) {
            Point a = hole.get(i), b = hole.get((i + 1) % hole.size());
            if (a.x() == b.x()) return new double[]{a.x(), (a.z() + b.z()) / 2.0 + 0.5};
        }
        return new double[]{hole.get(0).x(), hole.get(0).z() + 0.5};
    }

    private static boolean contains(List<Point> poly, double[] p) {
        boolean inside = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            Point a = poly.get(i), b = poly.get(j);
            if ((a.z() > p[1]) != (b.z() > p[1])) {
                double x = a.x() + (p[1] - a.z()) * (b.x() - a.x()) / (double) (b.z() - a.z());
                if (x > p[0]) inside = !inside;
            }
        }
        return inside;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
